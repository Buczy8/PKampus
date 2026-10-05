package pl.edu.pk.pkampus.modules.admin.residents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.common.util.AcademicYear;
import pl.edu.pk.pkampus.modules.admin.AdminResource;
import pl.edu.pk.pkampus.modules.admin.AdminAvatarUrls;
import pl.edu.pk.pkampus.modules.admin.AdminScope;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentResponseDto;
import pl.edu.pk.pkampus.modules.admin.dto.PendingResidentDto;
import pl.edu.pk.pkampus.modules.admin.dto.RejectResidentRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminResidentService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final MinioStorageService minioStorageService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PendingResidentDto> listPendingResidents(User admin) {
        List<User> pending = switch (admin.getRole()) {
            case SUPER_ADMIN -> userRepository.findAllByStatus(UserStatus.PENDING_APPROVAL);
            case DORM_ADMIN -> {
                UUID dormitoryId = AdminScope.requireDormitoryId(admin, AdminResource.APPLICATIONS);
                yield userRepository.findAllByDormitoryIdAndStatus(dormitoryId, UserStatus.PENDING_APPROVAL);
            }
            default -> throw new AccessDeniedException(
                    "Only dormitory administrators can manage residency applications");
        };

        return pending.stream()
                .filter(u -> u.getRole() == UserRole.RESIDENT)
                .map(this::toPendingDto)
                .toList();
    }

    @Transactional
    public ActivateResidentResponseDto activateResident(User admin, UUID residentId, ActivateResidentRequestDto request) {
        User resident = loadPendingResidentInScope(admin, residentId);

        String roomNumber = resolveTargetRoomNumber(request, resident);
        Room room = requireRoom(resident.getDormitory().getId(), roomNumber);

        if (roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId()).isPresent()) {
            throw new AccountStatusException("Resident already has an active room assignment");
        }

        String academicYear = AcademicYear.current(AdminScope.today(clock));
        RoomAssignment savedAssignment = roomAssignmentRepository.save(newAssignment(resident, room, academicYear));

        resident.setStatus(UserStatus.ACTIVE);
        resident.setDeclaredRoomNumber(roomNumber);
        userRepository.save(resident);

        eventPublisher.publishEvent(new ResidentActivatedEvent(
                resident.getId(),
                resident.getEmail(),
                resident.getFirstName(),
                roomNumber,
                resident.getDormitory().getName()
        ));

        log.info("ADS {} activated resident {} into room {} ({})",
                admin.getId(), resident.getId(), roomNumber, academicYear);

        return ActivateResidentResponseDto.builder()
                .userId(resident.getId())
                .status(UserStatus.ACTIVE)
                .roomAssignmentId(savedAssignment.getId())
                .roomNumber(roomNumber)
                .academicYear(academicYear)
                .message("Residency approved. Account is now ACTIVE.")
                .build();
    }

    @Transactional
    public void rejectResident(User admin, UUID residentId, RejectResidentRequestDto request) {
        User resident = loadPendingResidentInScope(admin, residentId);

        String email = resident.getEmail();
        String firstName = resident.getFirstName();
        String avatarObject = resident.getAvatarUrl();
        String reason = request.getReason().trim();

        userRepository.delete(resident);
        userRepository.flush();

        AdminAvatarUrls.removeQuietly(minioStorageService, avatarObject, residentId);

        eventPublisher.publishEvent(new RegistrationRejectedEvent(residentId, email, firstName, reason));
        log.info("ADS {} rejected resident application {} reason={}", admin.getId(), residentId, reason);
    }

    private static String resolveTargetRoomNumber(ActivateResidentRequestDto request, User resident) {
        String roomNumber = request != null && request.getRoomNumber() != null && !request.getRoomNumber().isBlank()
                ? request.getRoomNumber().trim()
                : resident.getDeclaredRoomNumber();

        if (roomNumber == null || roomNumber.isBlank()) {
            throw new BusinessRuleException("Room number is required to activate residency");
        }
        return roomNumber;
    }

    private Room requireRoom(UUID dormitoryId, String roomNumber) {
        return roomRepository.findByDormitoryIdAndRoomNumber(dormitoryId, roomNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room " + roomNumber + " was not found in this dormitory"));
    }

    private RoomAssignment newAssignment(User resident, Room room, String academicYear) {
        return RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear(academicYear)
                .isActive(true)
                .checkInDate(AdminScope.today(clock))
                .build();
    }

    private User loadPendingResidentInScope(User admin, UUID residentId) {
        User resident = userRepository.findById(residentId)
                .orElseThrow(() -> new ResourceNotFoundException("Resident application not found"));

        if (resident.getRole() != UserRole.RESIDENT) {
            throw new AccountStatusException("Only resident applications can be processed");
        }
        if (resident.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new AccountStatusException("Resident is not awaiting approval (status=" + resident.getStatus() + ")");
        }
        if (resident.getDormitory() == null) {
            throw new AccountStatusException("Resident has no dormitory assigned");
        }

        if (admin.getRole() == UserRole.SUPER_ADMIN) {
            return resident;
        }

        UUID adminDormId = AdminScope.requireDormitoryId(admin, AdminResource.APPLICATIONS);
        if (!adminDormId.equals(resident.getDormitory().getId())) {
            throw new AccessDeniedException("Resident does not belong to your dormitory");
        }

        return resident;
    }

    private PendingResidentDto toPendingDto(User user) {
        return PendingResidentDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .declaredRoomNumber(user.getDeclaredRoomNumber())
                .dormitoryId(user.getDormitory() != null ? user.getDormitory().getId() : null)
                .dormitoryName(user.getDormitory() != null ? user.getDormitory().getName() : null)
                .avatarUrl(AdminAvatarUrls.presignedOrNull(minioStorageService, user))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
