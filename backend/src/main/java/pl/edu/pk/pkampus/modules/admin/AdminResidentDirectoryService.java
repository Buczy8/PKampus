package pl.edu.pk.pkampus.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.Clock;
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

    private static final EnumSet<UserStatus> DIRECTORY_STATUSES =
            EnumSet.of(UserStatus.ACTIVE, UserStatus.BLOCKED);

    private final UserRepository userRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final SanctionRepository sanctionRepository;
    private final MinioStorageService minioStorageService;
    private final EmailService emailService;
    private final TokenRevocationService tokenRevocationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ManagedResidentDto> listResidents(User admin) {
        UUID dormitoryId = AdminScope.requireDormitoryId(admin, "residents");
        List<User> residents = userRepository
                .findAllByDormitoryIdAndRoleAndStatusInOrderByLastNameAscFirstNameAsc(
                        dormitoryId, UserRole.RESIDENT, DIRECTORY_STATUSES);

        List<UUID> ids = residents.stream().map(User::getId).toList();
        LocalDate today = AdminScope.today(clock);
        Map<UUID, Sanction> activeBans = ids.isEmpty()
                ? Map.of()
                : sanctionRepository.findActiveRoomBansForUsers(ids, today).stream()
                        .collect(Collectors.toMap(
                                s -> s.getUser().getId(),
                                Function.identity(),
                                (a, b) -> a.getEndDate().isAfter(b.getEndDate()) ? a : b
                        ));
        Map<UUID, RoomAssignment> assignmentByUser = ids.isEmpty()
                ? Map.of()
                : roomAssignmentRepository.findActiveByUserIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                a -> a.getUser().getId(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        return residents.stream()
                .map(u -> toManagedDto(u, activeBans.get(u.getId()), assignmentByUser.get(u.getId())))
                .toList();
    }

    @Transactional
    public ManagedResidentDto block(User admin, UUID residentId) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("Resident is already blocked");
        }
        resident.setStatus(UserStatus.BLOCKED);
        userRepository.save(resident);
        tokenRevocationService.revokeUser(resident.getId());
        emailService.sendAccountBlockedEmail(resident.getEmail(), resident.getFirstName());
        log.info("ADS {} blocked resident {}", admin.getEmail(), resident.getEmail());
        return toManagedDto(resident, currentRoomBan(resident.getId()), activeAssignment(resident.getId()));
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
        return toManagedDto(resident, currentRoomBan(resident.getId()), activeAssignment(resident.getId()));
    }

    @Transactional
    public ManagedResidentDto checkout(User admin, UUID residentId) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() == UserStatus.CHECKED_OUT) {
            throw new BusinessRuleException("Resident is already checked out");
        }

        roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId()).ifPresent(assignment -> {
            assignment.setIsActive(false);
            assignment.setCheckOutDate(AdminScope.today(clock));
            roomAssignmentRepository.save(assignment);
        });

        String avatarObject = resident.getAvatarUrl();
        resident.setStatus(UserStatus.CHECKED_OUT);
        resident.setAvatarUrl(null);
        userRepository.save(resident);
        tokenRevocationService.revokeUser(resident.getId());

        AdminAvatarUrls.removeQuietly(minioStorageService, avatarObject, residentId);

        emailService.sendCheckedOutEmail(resident.getEmail(), resident.getFirstName());
        log.info("ADS {} checked out resident {}", admin.getEmail(), resident.getEmail());
        return toManagedDto(resident, null, null);
    }

    @Transactional
    public SanctionDto issueRoomBan(User admin, UUID residentId, CreateRoomBanRequestDto request) {
        User resident = loadManageableResident(admin, residentId);
        if (resident.getStatus() != UserStatus.ACTIVE && resident.getStatus() != UserStatus.BLOCKED) {
            throw new BusinessRuleException("Room ban can only be issued for ACTIVE or BLOCKED residents");
        }

        LocalDate today = AdminScope.today(clock);
        List<Sanction> existing = sanctionRepository.findActiveByUserAndType(
                resident.getId(), SanctionType.ROOM_BAN, today);
        if (!existing.isEmpty()) {
            throw new BusinessRuleException("Resident already has an active ROOM_BAN");
        }

        LocalDate end = today.plusMonths(request.getDurationMonths());

        Sanction sanction = Sanction.builder()
                .user(resident)
                .issuedBy(admin)
                .dormitory(admin.getDormitory())
                .sanctionType(SanctionType.ROOM_BAN)
                .reason(request.getReason().trim())
                .startDate(today)
                .endDate(end)
                .active(true)
                .build();

        Sanction saved = sanctionRepository.save(sanction);
        emailService.sendRoomBanEmail(
                resident.getEmail(), resident.getFirstName(), today, end, saved.getReason());
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
        UUID dormitoryId = AdminScope.requireDormitoryId(admin, "residents");
        User resident = userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitoryId, UserRole.RESIDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Resident not found"));
        if (!DIRECTORY_STATUSES.contains(resident.getStatus())) {
            throw new BusinessRuleException("Resident status cannot be managed (" + resident.getStatus() + ")");
        }
        return resident;
    }

    private Sanction currentRoomBan(UUID userId) {
        return sanctionRepository.findActiveByUserAndType(userId, SanctionType.ROOM_BAN, AdminScope.today(clock))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private RoomAssignment activeAssignment(UUID userId) {
        return roomAssignmentRepository.findByUserIdAndIsActiveTrue(userId).orElse(null);
    }

    private ManagedResidentDto toManagedDto(User user, Sanction ban, RoomAssignment assignment) {
        String roomNumber = user.getDeclaredRoomNumber();
        if (assignment != null && assignment.getRoom() != null) {
            roomNumber = assignment.getRoom().getRoomNumber();
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
                .avatarUrl(AdminAvatarUrls.presignedOrNull(minioStorageService, user))
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
