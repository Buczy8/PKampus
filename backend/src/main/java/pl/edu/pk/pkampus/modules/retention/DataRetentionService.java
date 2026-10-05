package pl.edu.pk.pkampus.modules.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.board.BoardRetentionService;
import pl.edu.pk.pkampus.modules.issues.IssuePhoto;
import pl.edu.pk.pkampus.modules.issues.IssuePhotoRepository;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.retention.dto.DataRetentionReportDto;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final MinioStorageService minioStorageService;
    private final IssuePhotoRepository issuePhotoRepository;
    private final BoardRetentionService boardRetentionService;
    private final LaundryBookingRepository laundryBookingRepository;
    private final RoomBookingRepository roomBookingRepository;
    private final UserRepository userRepository;

    @Value("${app.scheduling.retention.issue-photos-days:30}")
    private int issuePhotosDays = 30;

    @Value("${app.scheduling.retention.posts-resolved-days:30}")
    private int postsResolvedDays = 30;

    @Value("${app.scheduling.retention.posts-deleted-days:14}")
    private int postsDeletedDays = 14;

    @Value("${app.scheduling.retention.comments-deleted-days:14}")
    private int commentsDeletedDays = 14;

    @Value("${app.scheduling.retention.bookings-purge-days:90}")
    private int bookingsPurgeDays = 90;

    @Value("${app.scheduling.retention.checked-out-anonymize-days:365}")
    private int checkedOutAnonymizeDays = 365;

    /**
     * Runs all retention tasks. Intentionally NOT wrapped in a single transaction:
     * MinIO deletions are non-transactional I/O and must not hold a DB connection,
     * and each purge step is independently transactional (see repository and
     * {@link BoardRetentionService} annotations) so partial progress survives
     * a later step failing.
     */
    public DataRetentionReportDto runRetentionTasks() {
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();
        log.info("Starting GDPR / RODO data retention execution at {}", now);

        int photosRemoved = purgeOldIssuePhotos(now);
        int postsRemoved = purgeOldPosts(now);
        int commentsRemoved = purgeOldComments(now);
        int laundryBookingsPurged = purgeOldLaundryBookings(now);
        int roomBookingsPurged = purgeOldRoomBookings(now);
        int usersAnonymized = anonymizeOldCheckedOutUsers(now);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Finished data retention tasks in {}ms: {} issue photos, {} posts, {} comments, {} laundry bookings, {} room bookings, {} users anonymized",
                durationMs, photosRemoved, postsRemoved, commentsRemoved, laundryBookingsPurged, roomBookingsPurged, usersAnonymized);

        return DataRetentionReportDto.builder()
                .executedAt(now)
                .issuePhotosRemovedCount(photosRemoved)
                .postsRemovedCount(postsRemoved)
                .commentsRemovedCount(commentsRemoved)
                .laundryBookingsPurgedCount(laundryBookingsPurged)
                .roomBookingsPurgedCount(roomBookingsPurged)
                .usersAnonymizedCount(usersAnonymized)
                .executionDurationMs(durationMs)
                .build();
    }

    private int purgeOldIssuePhotos(Instant now) {
        Instant cutoff = now.minus(issuePhotosDays, ChronoUnit.DAYS);
        List<IssueStatus> closedStatuses = List.of(IssueStatus.RESOLVED, IssueStatus.REJECTED);
        List<IssuePhoto> photos = issuePhotoRepository.findPhotosForOldClosedIssues(closedStatuses, cutoff);

        if (photos.isEmpty()) {
            return 0;
        }

        for (IssuePhoto photo : photos) {
            try {
                minioStorageService.removeIssuePhoto(photo.getPhotoUrl());
            } catch (Exception e) {
                log.warn("Failed to delete issue photo {} from MinIO during retention: {}", photo.getPhotoUrl(), e.getMessage());
            }
        }

        issuePhotoRepository.deleteAllInBatch(photos);
        log.info("Purged {} issue photos older than {} days", photos.size(), issuePhotosDays);
        return photos.size();
    }

    private int purgeOldPosts(Instant now) {
        Instant resolvedCutoff = now.minus(postsResolvedDays, ChronoUnit.DAYS);
        Instant deletedCutoff = now.minus(postsDeletedDays, ChronoUnit.DAYS);
        return boardRetentionService.purgeOldPosts(resolvedCutoff, deletedCutoff);
    }

    private int purgeOldComments(Instant now) {
        Instant cutoff = now.minus(commentsDeletedDays, ChronoUnit.DAYS);
        return boardRetentionService.purgeOldComments(cutoff);
    }

    private int purgeOldLaundryBookings(Instant now) {
        Instant cutoff = now.minus(bookingsPurgeDays, ChronoUnit.DAYS);
        List<LaundryBookingStatus> completedOrCancelled = List.of(
                LaundryBookingStatus.COMPLETED,
                LaundryBookingStatus.CANCELLED_USER,
                LaundryBookingStatus.AUTO_CANCELLED_15MIN,
                LaundryBookingStatus.CANCELLED_MACHINE_OUT_OF_ORDER
        );

        int removed = laundryBookingRepository.deleteOldCompletedOrCancelledBookings(completedOrCancelled, cutoff);
        if (removed > 0) {
            log.info("Purged {} laundry bookings older than {} days", removed, bookingsPurgeDays);
        }
        return removed;
    }

    private int purgeOldRoomBookings(Instant now) {
        Instant cutoff = now.minus(bookingsPurgeDays, ChronoUnit.DAYS);
        List<RoomBookingStatus> completedOrCancelled = List.of(
                RoomBookingStatus.COMPLETED,
                RoomBookingStatus.CANCELLED_USER,
                RoomBookingStatus.AUTO_CANCELLED_15MIN,
                RoomBookingStatus.CANCELLED_ROOM_MAINTENANCE
        );

        int removed = roomBookingRepository.deleteOldCompletedOrCancelledBookings(completedOrCancelled, cutoff);
        if (removed > 0) {
            log.info("Purged {} room bookings older than {} days", removed, bookingsPurgeDays);
        }
        return removed;
    }

    private int anonymizeOldCheckedOutUsers(Instant now) {
        Instant cutoff = now.minus(checkedOutAnonymizeDays, ChronoUnit.DAYS);
        List<User> users = userRepository.findCheckedOutUsersForAnonymization(cutoff);

        if (users.isEmpty()) {
            return 0;
        }

        for (User user : users) {
            user.setFirstName("Anonim");
            user.setLastName("Użytkownik");
            user.setEmail("anonymized-" + user.getId() + "@pkampus.local");
            user.setPhoneNumber("000000000");
            user.setDeclaredRoomNumber(null);
            user.setAvatarUrl(null);
            userRepository.save(user);
        }

        log.info("Anonymized {} checked-out user accounts older than {} days", users.size(), checkedOutAnonymizeDays);
        return users.size();
    }
}
