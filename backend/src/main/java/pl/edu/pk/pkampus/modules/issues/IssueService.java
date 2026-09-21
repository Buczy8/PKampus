package pl.edu.pk.pkampus.modules.issues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.issues.dto.CreateIssueRequestDto;
import pl.edu.pk.pkampus.modules.issues.dto.IssueDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.StaffIssueDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int PHOTO_PRESIGN_MINUTES = 30;

    private static final Set<String> COMMON_AREAS = Set.of(
            "kuchnia piętrowa",
            "węzeł sanitarny",
            "korytarz",
            "pralnia",
            "winda",
            "inne"
    );

    private static final Map<IssueStatus, Set<IssueStatus>> ALLOWED_TRANSITIONS =
            buildAllowedTransitions();

    private final IssueRepository issueRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final MinioStorageService minioStorageService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<IssueDto> listMyIssues(User user) {
        requireActiveResident(user);
        return issueRepository.findByReporterIdWithDetailsOrderByCreatedAtDesc(user.getId()).stream()
                .map(issue -> toDto(issue, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffIssueDto> listStaffIssues(
            UUID dormitoryId,
            Collection<IssueStatus> statuses,
            IssueCategory category,
            IssueUrgency urgency,
            LocalDate from,
            LocalDate to,
            String roomNumber,
            Integer floor
    ) {
        boolean statusesEmpty = statuses == null || statuses.isEmpty();
        Collection<IssueStatus> statusFilter = statusesEmpty
                ? EnumSet.noneOf(IssueStatus.class)
                : statuses;

        Instant createdFrom = from == null ? null : from.atStartOfDay(WARSAW).toInstant();
        Instant createdTo = to == null ? null : to.plusDays(1).atStartOfDay(WARSAW).toInstant();
        boolean roomNumberEmpty = roomNumber == null || roomNumber.isBlank();
        String roomFilter = roomNumberEmpty
                ? ""
                : roomNumber.trim().toLowerCase(Locale.ROOT);

        return issueRepository.findStaffFiltered(
                        dormitoryId,
                        statusesEmpty,
                        statusFilter,
                        category == null,
                        category != null ? category : IssueCategory.OTHER,
                        urgency == null,
                        urgency != null ? urgency : IssueUrgency.NORMAL,
                        createdFrom == null,
                        createdFrom != null ? createdFrom : Instant.EPOCH,
                        createdTo == null,
                        createdTo != null ? createdTo : Instant.EPOCH,
                        roomNumberEmpty,
                        roomFilter,
                        floor == null,
                        floor != null ? floor : 0
                ).stream()
                .map(issue -> toStaffDto(issue, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffIssueDto getStaffIssue(UUID dormitoryId, UUID issueId) {
        Issue issue = issueRepository.findByIdAndDormitoryIdWithDetails(issueId, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        return toStaffDto(issue, true);
    }

    @Transactional
    public StaffIssueDto updateStaffIssueStatus(
            UUID dormitoryId,
            UUID issueId,
            IssueStatus newStatus,
            String staffNotes
    ) {
        Issue issue = issueRepository.findByIdAndDormitoryIdWithDetails(issueId, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));

        IssueStatus current = issue.getStatus();
        if (current == newStatus) {
            throw new BusinessRuleException("Issue is already in status " + newStatus);
        }
        Set<IssueStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BusinessRuleException(
                    "Illegal status transition from " + current + " to " + newStatus);
        }

        if (staffNotes != null) {
            String trimmed = staffNotes.trim();
            if (newStatus == IssueStatus.REJECTED && trimmed.isEmpty()) {
                throw new BusinessRuleException("staffNotes is required when rejecting an issue");
            }
            issue.setStaffNotes(trimmed.isEmpty() ? null : trimmed);
        } else if (newStatus == IssueStatus.REJECTED
                && (issue.getStaffNotes() == null || issue.getStaffNotes().isBlank())) {
            throw new BusinessRuleException("staffNotes is required when rejecting an issue");
        }

        issue.setStatus(newStatus);
        Issue saved = issueRepository.save(issue);

        User reporter = saved.getReporter();
        emailService.sendIssueStatusChangedEmail(
                reporter.getEmail(),
                reporter.getFirstName(),
                newStatus.name(),
                saved.getStaffNotes()
        );

        log.info("Staff updated issue {} to {} in dormitory {}", saved.getId(), newStatus, dormitoryId);
        return toStaffDto(saved, true);
    }

    @Transactional
    public IssueDto createIssue(User user, CreateIssueRequestDto request, MultipartFile photo) {
        Dormitory dorm = requireActiveResident(user);

        String description = request.description().trim();
        if (description.isEmpty()) {
            throw new BusinessRuleException("Description is required");
        }

        Room room = null;
        String commonAreaName = null;

        if (request.locationType() == IssueLocationType.MY_ROOM) {
            RoomAssignment assignment = roomAssignmentRepository
                    .findByUserIdAndIsActiveTrue(user.getId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Brak aktywnego meldunku — nie można zgłosić usterki w pokoju"));
            room = assignment.getRoom();
            if (!room.getDormitory().getId().equals(dorm.getId())) {
                throw new BusinessRuleException("Assigned room does not belong to your dormitory");
            }
        } else if (request.locationType() == IssueLocationType.COMMON_AREA) {
            commonAreaName = normalizeCommonArea(request.commonAreaName());
        } else {
            throw new BusinessRuleException("Invalid location type");
        }

        String uploadedObject = null;
        try {
            Issue issue = Issue.builder()
                    .reporter(user)
                    .dormitory(dorm)
                    .room(room)
                    .commonAreaName(commonAreaName)
                    .category(request.category())
                    .urgency(request.urgency())
                    .description(description)
                    .status(IssueStatus.NEW)
                    .build();

            if (photo != null && !photo.isEmpty()) {
                uploadedObject = minioStorageService.uploadIssuePhoto(photo);
                String originalName = photo.getOriginalFilename();
                if (originalName == null || originalName.isBlank()) {
                    originalName = uploadedObject;
                }
                IssuePhoto issuePhoto = IssuePhoto.builder()
                        .photoUrl(uploadedObject)
                        .fileName(originalName.length() > 255 ? originalName.substring(0, 255) : originalName)
                        .fileSizeBytes((int) Math.min(photo.getSize(), Integer.MAX_VALUE))
                        .build();
                issue.addPhoto(issuePhoto);
            }

            Issue saved = issueRepository.saveAndFlush(issue);
            log.info("Resident {} reported issue {} ({})", user.getEmail(), saved.getId(), saved.getCategory());
            return toDto(saved, true);
        } catch (RuntimeException ex) {
            if (uploadedObject != null) {
                try {
                    minioStorageService.removeIssuePhoto(uploadedObject);
                } catch (Exception cleanupEx) {
                    log.error("Failed to cleanup orphaned issue photo {}", uploadedObject, cleanupEx);
                }
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public String getOwnIssuePhotoPresignedUrl(User user, UUID issueId) {
        requireActiveResident(user);
        Issue issue = issueRepository.findByIdAndReporterIdWithDetails(issueId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
        if (issue.getPhotos().isEmpty()) {
            throw new ResourceNotFoundException("Issue has no photo");
        }
        String objectKey = issue.getPhotos().getFirst().getPhotoUrl();
        return minioStorageService.getIssuePresignedUrl(objectKey, PHOTO_PRESIGN_MINUTES);
    }

    private Dormitory requireActiveResident(User user) {
        if (user.getRole() != UserRole.RESIDENT) {
            throw new AccountStatusException("Only residents can report issues");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("Account must be ACTIVE to report issues");
        }
        if (user.getDormitory() == null) {
            throw new AccountStatusException("Resident is not assigned to a dormitory");
        }
        return user.getDormitory();
    }

    private String normalizeCommonArea(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("commonAreaName is required for COMMON_AREA");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!COMMON_AREAS.contains(normalized)) {
            throw new BusinessRuleException(
                    "commonAreaName must be one of: kuchnia piętrowa, węzeł sanitarny, korytarz, pralnia, winda, inne");
        }
        return normalized;
    }

    private StaffIssueDto toStaffDto(Issue issue, boolean includePresignedPhoto) {
        String roomNumber = null;
        Integer floor = null;
        String commonArea = issue.getCommonAreaName();
        String locationLabel;
        if (issue.getRoom() != null) {
            roomNumber = issue.getRoom().getRoomNumber();
            floor = issue.getRoom().getFloor();
            locationLabel = "Pokój " + roomNumber;
        } else {
            locationLabel = capitalizeCommonArea(commonArea);
        }

        boolean hasPhoto = issue.getPhotos() != null && !issue.getPhotos().isEmpty();
        String photoUrl = null;
        if (hasPhoto && includePresignedPhoto) {
            try {
                photoUrl = minioStorageService.getIssuePresignedUrl(
                        issue.getPhotos().getFirst().getPhotoUrl(), PHOTO_PRESIGN_MINUTES);
            } catch (Exception e) {
                log.warn("Could not generate issue photo URL for {}: {}", issue.getId(), e.getMessage());
            }
        }

        User reporter = issue.getReporter();
        return new StaffIssueDto(
                issue.getId(),
                locationLabel,
                roomNumber,
                floor,
                commonArea,
                issue.getCategory(),
                issue.getUrgency(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getStaffNotes(),
                hasPhoto,
                photoUrl,
                reporter.getFirstName(),
                reporter.getLastName(),
                issue.getCreatedAt().atZone(WARSAW).toOffsetDateTime(),
                issue.getUpdatedAt().atZone(WARSAW).toOffsetDateTime()
        );
    }

    private IssueDto toDto(Issue issue, boolean includePresignedPhoto) {
        String locationLabel;
        UUID roomId = null;
        String commonArea = issue.getCommonAreaName();
        if (issue.getRoom() != null) {
            roomId = issue.getRoom().getId();
            locationLabel = "Pokój " + issue.getRoom().getRoomNumber();
        } else {
            locationLabel = capitalizeCommonArea(commonArea);
        }

        boolean hasPhoto = issue.getPhotos() != null && !issue.getPhotos().isEmpty();
        String photoUrl = null;
        if (hasPhoto && includePresignedPhoto) {
            try {
                photoUrl = minioStorageService.getIssuePresignedUrl(
                        issue.getPhotos().getFirst().getPhotoUrl(), PHOTO_PRESIGN_MINUTES);
            } catch (Exception e) {
                log.warn("Could not generate issue photo URL for {}: {}", issue.getId(), e.getMessage());
            }
        }

        return new IssueDto(
                issue.getId(),
                locationLabel,
                roomId,
                commonArea,
                issue.getCategory(),
                issue.getUrgency(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getStaffNotes(),
                hasPhoto,
                photoUrl,
                issue.getCreatedAt().atZone(WARSAW).toOffsetDateTime(),
                issue.getUpdatedAt().atZone(WARSAW).toOffsetDateTime()
        );
    }

    private static String capitalizeCommonArea(String name) {
        if (name == null || name.isBlank()) {
            return "Część wspólna";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private static Map<IssueStatus, Set<IssueStatus>> buildAllowedTransitions() {
        Map<IssueStatus, Set<IssueStatus>> map = new EnumMap<>(IssueStatus.class);
        map.put(IssueStatus.NEW, EnumSet.of(
                IssueStatus.ASSIGNED_TO_MAINTENANCE, IssueStatus.REJECTED));
        map.put(IssueStatus.ASSIGNED_TO_MAINTENANCE, EnumSet.of(
                IssueStatus.IN_PROGRESS, IssueStatus.REJECTED));
        map.put(IssueStatus.IN_PROGRESS, EnumSet.of(
                IssueStatus.PARTS_REQUIRED, IssueStatus.RESOLVED));
        map.put(IssueStatus.PARTS_REQUIRED, EnumSet.of(
                IssueStatus.IN_PROGRESS, IssueStatus.RESOLVED));
        map.put(IssueStatus.RESOLVED, EnumSet.noneOf(IssueStatus.class));
        map.put(IssueStatus.REJECTED, EnumSet.noneOf(IssueStatus.class));
        return Map.copyOf(map);
    }
}
