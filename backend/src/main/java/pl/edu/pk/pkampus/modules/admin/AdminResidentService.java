package pl.edu.pk.pkampus.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.common.util.AcademicYear;
import pl.edu.pk.pkampus.mail.EmailService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminResidentService {

    private static final int AVATAR_PRESIGN_MINUTES = 60;

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final MinioStorageService minioStorageService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<PendingResidentDto> listPendingResidents(User admin) {
        UUID dormitoryId = resolveAdminDormitoryId(admin);

        return userRepository.findAllByDormitoryIdAndStatus(dormitoryId, UserStatus.PENDING_APPROVAL)
                .stream()
                .filter(u -> u.getRole() == UserRole.RESIDENT)
                .map(this::toPendingDto)
                .toList();
    }

    @Transactional
    public ActivateResidentResponseDto activateResident(User admin, UUID residentId, ActivateResidentRequestDto request) {
        User resident = loadPendingResidentInScope(admin, residentId);

        String roomNumber = request != null && request.getRoomNumber() != null && !request.getRoomNumber().isBlank()
                ? request.getRoomNumber().trim()
                : resident.getDeclaredRoomNumber();

        if (roomNumber == null || roomNumber.isBlank()) {
            throw new IllegalArgumentException("Room number is required to activate residency");
        }

        UUID dormitoryId = resident.getDormitory().getId();
        Room room = roomRepository.findByDormitoryIdAndRoomNumber(dormitoryId, roomNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room " + roomNumber + " was not found in this dormitory"));

        if (roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId()).isPresent()) {
            throw new AccountStatusException("Resident already has an active room assignment");
        }

        String academicYear = AcademicYear.current();
        RoomAssignment assignment = RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear(academicYear)
                .isActive(true)
                .checkInDate(LocalDate.now())
                .build();
        RoomAssignment savedAssignment = roomAssignmentRepository.save(assignment);

        resident.setStatus(UserStatus.ACTIVE);
        resident.setDeclaredRoomNumber(roomNumber);
        userRepository.save(resident);

        emailService.sendAccountActivatedEmail(
                resident.getEmail(),
                resident.getFirstName(),
                roomNumber,
                resident.getDormitory().getName()
        );

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

        if (avatarObject != null && !avatarObject.isBlank()) {
            try {
                minioStorageService.removeAvatar(avatarObject);
            } catch (Exception e) {
                log.error("Failed to remove avatar {} after rejecting resident {}", avatarObject, residentId, e);
            }
        }

        emailService.sendRegistrationRejectedEmail(email, firstName, reason);
        log.info("ADS {} rejected resident application {} reason={}", admin.getId(), residentId, reason);
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

        UUID adminDormId = resolveAdminDormitoryId(admin);
        if (!adminDormId.equals(resident.getDormitory().getId())) {
            throw new AccessDeniedException("Resident does not belong to your dormitory");
        }

        return resident;
    }

    private UUID resolveAdminDormitoryId(User admin) {
        if (admin.getRole() == UserRole.SUPER_ADMIN) {
            if (admin.getDormitory() != null) {
                return admin.getDormitory().getId();
            }
            throw new IllegalArgumentException(
                    "SUPER_ADMIN must have a dormitory context to manage pending residents in MVP");
        }
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage residency applications");
        }
        if (admin.getDormitory() == null) {
            throw new AccountStatusException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory().getId();
    }

    private PendingResidentDto toPendingDto(User user) {
        String avatarPresigned = null;
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            try {
                avatarPresigned = minioStorageService.getAvatarPresignedUrl(user.getAvatarUrl(), AVATAR_PRESIGN_MINUTES);
            } catch (Exception e) {
                log.warn("Could not generate avatar URL for user {}: {}", user.getId(), e.getMessage());
            }
        }

        return PendingResidentDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .declaredRoomNumber(user.getDeclaredRoomNumber())
                .dormitoryId(user.getDormitory() != null ? user.getDormitory().getId() : null)
                .dormitoryName(user.getDormitory() != null ? user.getDormitory().getName() : null)
                .avatarUrl(avatarPresigned)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
