package pl.edu.pk.pkampus.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.admin.dto.CreateRoomBanRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ManagedResidentDto;
import pl.edu.pk.pkampus.modules.admin.dto.SanctionDto;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.sanctions.Sanction;
import pl.edu.pk.pkampus.modules.sanctions.SanctionRepository;
import pl.edu.pk.pkampus.modules.sanctions.SanctionType;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminResidentDirectoryService {

    private static final int AVATAR_PRESIGN_MINUTES = 60;
    private static final EnumSet<UserStatus> DIRECTORY_STATUSES =
            EnumSet.of(UserStatus.ACTIVE, UserStatus.BLOCKED);

    private final UserRepository userRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final SanctionRepository sanctionRepository;
    private final MinioStorageService minioStorageService;
    private final EmailService emailService;
    private final TokenRevocationService tokenRevocationService;

    @Transactional(readOnly = true)
    public List<ManagedResidentDto> listResidents(User admin) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        List<User> residents = userRepository
                .findAllByDormitoryIdAndRoleAndStatusInOrderByLastNameAscFirstNameAsc(
                        dormitoryId, UserRole.RESIDENT, DIRECTORY_STATUSES);

        List<UUID> ids = residents.stream().map(User::getId).toList();
        Map<UUID, Sanction> activeBans = ids.isEmpty()
                ? Map.of()
                : sanctionRepository.findActiveRoomBansForUsers(ids, LocalDate.now()).stream()
                        .collect(Collectors.toMap(
                                s -> s.getUser().getId(),
                                Function.identity(),
                                (a, b) -> a.getEndDate().isAfter(b.getEndDate()) ? a : b
                        ));

        return residents.stream()
                .map(u -> toManagedDto(u, activeBans.get(u.getId())))
                .toList();
    }

    @Transactional
    public ManagedResidentDto block(User admin, UUID residentId) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessRuleException("Resident is already blocked");
        }
        if (resident.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("Only ACTIVE residents can be blocked");
        }
        resident.setStatus(UserStatus.BLOCKED);
        userRepository.save(resident);
        tokenRevocationService.revokeUser(resident.getId());
        emailService.sendHtmlEmail(
                resident.getEmail(),
                "PKampus - Account blocked",
                """
                <p>Hello %s,</p>
                <p>Your PKampus account has been blocked by the dormitory administration.
                Contact your dormitory office for details.</p>
                """.formatted(resident.getFirstName())
        );
        log.info("ADS {} blocked resident {}", admin.getEmail(), resident.getEmail());
        return toManagedDto(resident, currentRoomBan(resident.getId()));
    }

    @Transactional
    public ManagedResidentDto unblock(User admin, UUID residentId) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() != UserStatus.BLOCKED) {
            throw new BusinessRuleException("Resident is not blocked");
        }
        resident.setStatus(UserStatus.ACTIVE);
        userRepository.save(resident);
        tokenRevocationService.clearRevocation(resident.getId());
        log.info("ADS {} unblocked resident {}", admin.getEmail(), resident.getEmail());
        return toManagedDto(resident, currentRoomBan(resident.getId()));
    }

    @Transactional
    public ManagedResidentDto checkout(User admin, UUID residentId) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() == UserStatus.CHECKED_OUT) {
            throw new BusinessRuleException("Resident is already checked out");
        }

        roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId()).ifPresent(assignment -> {
            assignment.setIsActive(false);
            assignment.setCheckOutDate(LocalDate.now());
            roomAssignmentRepository.save(assignment);
        });

        String avatarObject = resident.getAvatarUrl();
        resident.setStatus(UserStatus.CHECKED_OUT);
        resident.setAvatarUrl(null);
        userRepository.save(resident);
        tokenRevocationService.revokeUser(resident.getId());

        if (avatarObject != null && !avatarObject.isBlank()) {
            try {
                minioStorageService.removeAvatar(avatarObject);
            } catch (Exception e) {
                log.error("Failed to remove avatar after checkout of {}", residentId, e);
            }
        }

        emailService.sendHtmlEmail(
                resident.getEmail(),
                "PKampus - Checked out",
                """
                <p>Hello %s,</p>
                <p>Your residency in PKampus has been closed (checked out).
                The account can no longer be used to sign in.</p>
                """.formatted(resident.getFirstName())
        );
        log.info("ADS {} checked out resident {}", admin.getEmail(), resident.getEmail());
        return toManagedDto(resident, null);
    }

    @Transactional
    public SanctionDto issueRoomBan(User admin, UUID residentId, CreateRoomBanRequestDto request) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() != UserStatus.ACTIVE && resident.getStatus() != UserStatus.BLOCKED) {
            throw new BusinessRuleException("Room ban can only be issued for ACTIVE or BLOCKED residents");
        }

        List<Sanction> existing = sanctionRepository.findActiveByUserAndType(
                resident.getId(), SanctionType.ROOM_BAN, LocalDate.now());
        if (!existing.isEmpty()) {
            throw new BusinessRuleException("Resident already has an active ROOM_BAN");
        }

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(request.getDurationMonths());

        Sanction sanction = Sanction.builder()
                .user(resident)
                .issuedBy(admin)
                .dormitory(admin.getDormitory())
                .sanctionType(SanctionType.ROOM_BAN)
                .reason(request.getReason().trim())
                .startDate(start)
                .endDate(end)
                .active(true)
                .build();

        Sanction saved = sanctionRepository.save(sanction);
        emailService.sendHtmlEmail(
                resident.getEmail(),
                "PKampus - Room reservation ban",
                """
                <p>Hello %s,</p>
                <p>A room reservation ban (ROOM_BAN) has been registered for your account.</p>
                <p><strong>Period:</strong> %s – %s<br>
                <strong>Reason:</strong> %s</p>
                <p>During this period you cannot book thematic rooms across the campus.</p>
                """.formatted(resident.getFirstName(), start, end, saved.getReason())
        );
        log.info("ADS {} issued ROOM_BAN to {} until {}", admin.getEmail(), resident.getEmail(), end);
        return toSanctionDto(saved);
    }

    @Transactional
    public SanctionDto revokeRoomBan(User admin, UUID residentId, UUID sanctionId) {
        loadManageableResident(admin, residentId);
        Sanction sanction = sanctionRepository.findByIdAndUserId(sanctionId, residentId)
                .orElseThrow(() -> new ResourceNotFoundException("Sanction not found"));
        if (sanction.getSanctionType() != SanctionType.ROOM_BAN) {
            throw new BusinessRuleException("Only ROOM_BAN sanctions can be revoked here");
        }
        if (!sanction.isActive()) {
            throw new BusinessRuleException("Sanction is already inactive");
        }
        sanction.setActive(false);
        Sanction saved = sanctionRepository.save(sanction);
        log.info("ADS {} revoked ROOM_BAN {} for resident {}", admin.getEmail(), sanctionId, residentId);
        return toSanctionDto(saved);
    }

    private User loadManageableResident(User admin, UUID residentId) {
        UUID dormitoryId = requireDormAdminDormitoryId(admin);
        User resident = userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitoryId, UserRole.RESIDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Resident not found"));
        if (!DIRECTORY_STATUSES.contains(resident.getStatus())) {
            throw new BusinessRuleException("Resident status cannot be managed (" + resident.getStatus() + ")");
        }
        return resident;
    }

    private UUID requireDormAdminDormitoryId(User admin) {
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage residents");
        }
        if (admin.getDormitory() == null) {
            throw new BusinessRuleException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory().getId();
    }

    private Sanction currentRoomBan(UUID userId) {
        return sanctionRepository.findActiveByUserAndType(userId, SanctionType.ROOM_BAN, LocalDate.now())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ManagedResidentDto toManagedDto(User user, Sanction ban) {
        String roomNumber = user.getDeclaredRoomNumber();
        RoomAssignment assignment = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId()).orElse(null);
        if (assignment != null && assignment.getRoom() != null) {
            roomNumber = assignment.getRoom().getRoomNumber();
        }

        String avatarPresigned = null;
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            try {
                avatarPresigned = minioStorageService.getAvatarPresignedUrl(user.getAvatarUrl(), AVATAR_PRESIGN_MINUTES);
            } catch (Exception e) {
                log.warn("Could not generate avatar URL for {}: {}", user.getId(), e.getMessage());
            }
        }

        ManagedResidentDto.ActiveRoomBanDto banDto = null;
        if (ban != null) {
            banDto = ManagedResidentDto.ActiveRoomBanDto.builder()
                    .id(ban.getId())
                    .startDate(ban.getStartDate())
                    .endDate(ban.getEndDate())
                    .reason(ban.getReason())
                    .build();
        }

        return ManagedResidentDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .roomNumber(roomNumber)
                .status(user.getStatus())
                .avatarUrl(avatarPresigned)
                .createdAt(user.getCreatedAt())
                .activeRoomBan(banDto)
                .build();
    }

    private SanctionDto toSanctionDto(Sanction sanction) {
        return SanctionDto.builder()
                .id(sanction.getId())
                .userId(sanction.getUser().getId())
                .sanctionType(sanction.getSanctionType())
                .reason(sanction.getReason())
                .startDate(sanction.getStartDate())
                .endDate(sanction.getEndDate())
                .active(sanction.isActive())
                .build();
    }
}
