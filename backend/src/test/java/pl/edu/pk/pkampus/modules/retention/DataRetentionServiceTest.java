package pl.edu.pk.pkampus.modules.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.board.BoardRetentionService;
import pl.edu.pk.pkampus.modules.issues.IssuePhoto;
import pl.edu.pk.pkampus.modules.issues.IssuePhotoRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.retention.dto.DataRetentionReportDto;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataRetentionService POJO/Mockito unit tests (AAA)")
class DataRetentionServiceTest {

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private IssuePhotoRepository issuePhotoRepository;

    @Mock
    private BoardRetentionService boardRetentionService;

    @Mock
    private LaundryBookingRepository laundryBookingRepository;

    @Mock
    private RoomBookingRepository roomBookingRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    @Test
    @DisplayName("runRetentionTasks returns empty report when no records match retention cutoffs")
    void runRetentionTasksEmpty() {
        // Arrange
        when(issuePhotoRepository.findPhotosForOldClosedIssues(any(), any())).thenReturn(List.of());
        when(boardRetentionService.purgeOldPosts(any(), any())).thenReturn(0);
        when(boardRetentionService.purgeOldComments(any())).thenReturn(0);
        when(laundryBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(roomBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(userRepository.findCheckedOutUsersForAnonymization(any())).thenReturn(List.of());

        // Act
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        // Assert
        assertNotNull(report);
        assertNotNull(report.getExecutedAt());
        assertEquals(0, report.getIssuePhotosRemovedCount());
        assertEquals(0, report.getPostsRemovedCount());
        assertEquals(0, report.getCommentsRemovedCount());
        assertEquals(0, report.getLaundryBookingsPurgedCount());
        assertEquals(0, report.getRoomBookingsPurgedCount());
        assertEquals(0, report.getUsersAnonymizedCount());
        assertTrue(report.getExecutionDurationMs() >= 0);
    }

    @Test
    @DisplayName("runRetentionTasks purges old issue photos from MinIO and repository")
    void runRetentionTasksPurgesIssuePhotos() {
        // Arrange
        IssuePhoto photo1 = IssuePhoto.builder()
                .id(UUID.randomUUID())
                .photoUrl("issues/p1.jpg")
                .fileName("p1.jpg")
                .build();
        IssuePhoto photo2 = IssuePhoto.builder()
                .id(UUID.randomUUID())
                .photoUrl("issues/p2.jpg")
                .fileName("p2.jpg")
                .build();

        when(issuePhotoRepository.findPhotosForOldClosedIssues(any(), any())).thenReturn(List.of(photo1, photo2));
        when(boardRetentionService.purgeOldPosts(any(), any())).thenReturn(0);
        when(boardRetentionService.purgeOldComments(any())).thenReturn(0);
        when(laundryBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(roomBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(userRepository.findCheckedOutUsersForAnonymization(any())).thenReturn(List.of());

        // Act
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        // Assert
        assertEquals(2, report.getIssuePhotosRemovedCount());
        verify(minioStorageService).removeIssuePhoto("issues/p1.jpg");
        verify(minioStorageService).removeIssuePhoto("issues/p2.jpg");
        verify(issuePhotoRepository).deleteAllInBatch(List.of(photo1, photo2));
    }

    @Test
    @DisplayName("runRetentionTasks handles MinIO deletion error gracefully without aborting batch delete")
    void runRetentionTasksHandlesMinioException() {
        // Arrange
        IssuePhoto photo = IssuePhoto.builder()
                .id(UUID.randomUUID())
                .photoUrl("issues/missing.jpg")
                .fileName("missing.jpg")
                .build();

        when(issuePhotoRepository.findPhotosForOldClosedIssues(any(), any())).thenReturn(List.of(photo));
        doThrow(new RuntimeException("MinIO error")).when(minioStorageService).removeIssuePhoto("issues/missing.jpg");
        when(boardRetentionService.purgeOldPosts(any(), any())).thenReturn(0);
        when(boardRetentionService.purgeOldComments(any())).thenReturn(0);
        when(laundryBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(roomBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(userRepository.findCheckedOutUsersForAnonymization(any())).thenReturn(List.of());

        // Act
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        // Assert
        assertEquals(1, report.getIssuePhotosRemovedCount());
        verify(issuePhotoRepository).deleteAllInBatch(List.of(photo));
    }

    @Test
    @DisplayName("runRetentionTasks purges old posts and their comments")
    void runRetentionTasksPurgesPosts() {
        // Arrange
        when(issuePhotoRepository.findPhotosForOldClosedIssues(any(), any())).thenReturn(List.of());
        when(boardRetentionService.purgeOldPosts(any(), any())).thenReturn(2);
        when(boardRetentionService.purgeOldComments(any())).thenReturn(0);
        when(laundryBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(roomBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(userRepository.findCheckedOutUsersForAnonymization(any())).thenReturn(List.of());

        // Act
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        // Assert
        assertEquals(2, report.getPostsRemovedCount());
        verify(boardRetentionService).purgeOldPosts(any(), any());
    }

    @Test
    @DisplayName("runRetentionTasks purges standalone comments, laundry and room bookings")
    void runRetentionTasksPurgesBookingsAndComments() {
        // Arrange
        when(issuePhotoRepository.findPhotosForOldClosedIssues(any(), any())).thenReturn(List.of());
        when(boardRetentionService.purgeOldPosts(any(), any())).thenReturn(0);
        when(boardRetentionService.purgeOldComments(any())).thenReturn(5);
        when(laundryBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(8);
        when(roomBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(3);
        when(userRepository.findCheckedOutUsersForAnonymization(any())).thenReturn(List.of());

        // Act
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        // Assert
        assertEquals(5, report.getCommentsRemovedCount());
        assertEquals(8, report.getLaundryBookingsPurgedCount());
        assertEquals(3, report.getRoomBookingsPurgedCount());
        verify(boardRetentionService).purgeOldComments(any());
    }

    @Test
    @DisplayName("runRetentionTasks anonymizes old checked out users")
    void runRetentionTasksAnonymizesUsers() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User checkedOutUser = User.builder()
                .id(userId)
                .email("student@pk.edu.pl")
                .firstName("Michał")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .declaredRoomNumber("101")
                .avatarUrl("avatars/user.jpg")
                .role(UserRole.RESIDENT)
                .status(UserStatus.CHECKED_OUT)
                .build();

        when(issuePhotoRepository.findPhotosForOldClosedIssues(any(), any())).thenReturn(List.of());
        when(boardRetentionService.purgeOldPosts(any(), any())).thenReturn(0);
        when(boardRetentionService.purgeOldComments(any())).thenReturn(0);
        when(laundryBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(roomBookingRepository.deleteOldCompletedOrCancelledBookings(any(), any())).thenReturn(0);
        when(userRepository.findCheckedOutUsersForAnonymization(any())).thenReturn(List.of(checkedOutUser));

        // Act
        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        // Assert
        assertEquals(1, report.getUsersAnonymizedCount());
        assertEquals("Anonim", checkedOutUser.getFirstName());
        assertEquals("Użytkownik", checkedOutUser.getLastName());
        assertEquals("anonymized-" + userId + "@pkampus.local", checkedOutUser.getEmail());
        assertEquals("000000000", checkedOutUser.getPhoneNumber());
        assertNull(checkedOutUser.getDeclaredRoomNumber());
        assertNull(checkedOutUser.getAvatarUrl());
        verify(userRepository).save(checkedOutUser);
    }
}
