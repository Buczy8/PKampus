package pl.edu.pk.pkampus.modules.issues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueService unit tests (AAA)")
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private EmailService emailService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private IssueService issueService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private Room room;
    private User resident;
    private Issue issue;
    private UUID issueId;

    @BeforeEach
    void setUp() {
        // TransactionTemplate runs the persistence callback inline (no Spring proxy in unit tests)
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(inv -> {
                    TransactionCallback<?> callback = inv.getArgument(0);
                    return callback.doInTransaction(null);
                });

        dorm1 = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-1")
                .build();

        dorm2 = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-2")
                .build();

        room = Room.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm1)
                .roomNumber("205")
                .floor(2)
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Kamil")
                .lastName("Nowak")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("205")
                .build();

        issueId = UUID.randomUUID();
        issue = Issue.builder()
                .id(issueId)
                .reporter(resident)
                .dormitory(dorm1)
                .room(room)
                .category(IssueCategory.PLUMBING)
                .urgency(IssueUrgency.URGENT)
                .description("Cieknie kran")
                .status(IssueStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("listMyIssues")
    class ListMyIssues {

        @Test
        @DisplayName("Should return resident's issues mapped to DTO")
        void listMyIssuesSuccess() {
            // Arrange
            IssuePhoto photo = IssuePhoto.builder()
                    .id(UUID.randomUUID())
                    .photoUrl("issues/photo1.jpg")
                    .build();
            issue.addPhoto(photo);

            when(issueRepository.findByReporterIdWithDetailsOrderByCreatedAtDesc(resident.getId()))
                    .thenReturn(List.of(issue));
            when(minioStorageService.getIssuePresignedUrl("issues/photo1.jpg", 30))
                    .thenReturn("https://minio/photo1.jpg");

            // Act
            List<IssueDto> result = issueService.listMyIssues(resident);

            // Assert
            assertEquals(1, result.size());
            IssueDto dto = result.getFirst();
            assertEquals(issueId, dto.id());
            assertEquals("Pokój 205", dto.locationLabel());
            assertEquals(IssueStatus.NEW, dto.status());
            assertTrue(dto.hasPhoto());
            assertEquals("https://minio/photo1.jpg", dto.photoUrl());
        }

        @Test
        @DisplayName("Should throw AccountStatusException when user is not active resident")
        void listMyIssuesThrowsWhenNotActiveResident() {
            // Arrange
            resident.setStatus(UserStatus.BLOCKED);

            // Act & Assert
            assertThrows(AccountStatusException.class, () -> issueService.listMyIssues(resident));
        }
    }

    @Nested
    @DisplayName("createIssue")
    class CreateIssue {

        @Test
        @DisplayName("Should create MY_ROOM issue with active room assignment")
        void createMyRoomIssueSuccess() {
            // Arrange
            CreateIssueRequestDto request = new CreateIssueRequestDto(
                    IssueLocationType.MY_ROOM,
                    null,
                    IssueCategory.PLUMBING,
                    IssueUrgency.URGENT,
                    "  Zepsuta spłuczka  "
            );

            RoomAssignment assignment = RoomAssignment.builder()
                    .room(room)
                    .isActive(true)
                    .build();

            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId()))
                    .thenReturn(Optional.of(assignment));
            when(issueRepository.saveAndFlush(any(Issue.class))).thenAnswer(inv -> {
                Issue i = inv.getArgument(0);
                i.setId(UUID.randomUUID());
                i.setCreatedAt(Instant.now());
                i.setUpdatedAt(Instant.now());
                return i;
            });

            // Act
            IssueDto result = issueService.createIssue(resident, request, null);

            // Assert
            assertNotNull(result);
            assertEquals("Pokój 205", result.locationLabel());
            assertEquals(room.getId(), result.roomId());
            assertEquals("Zepsuta spłuczka", result.description());
            assertEquals(IssueStatus.NEW, result.status());

            ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
            verify(issueRepository).saveAndFlush(captor.capture());
            Issue saved = captor.getValue();
            assertEquals("Zepsuta spłuczka", saved.getDescription());
            assertEquals(room, saved.getRoom());
            assertNull(saved.getCommonAreaName());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when MY_ROOM requested without active assignment")
        void createMyRoomThrowsWithoutAssignment() {
            // Arrange
            CreateIssueRequestDto request = new CreateIssueRequestDto(
                    IssueLocationType.MY_ROOM,
                    null,
                    IssueCategory.PLUMBING,
                    IssueUrgency.URGENT,
                    "Opis"
            );

            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> issueService.createIssue(resident, request, null));
            assertTrue(ex.getMessage().contains("Brak aktywnego meldunku"));
        }

        @Test
        @DisplayName("Should create COMMON_AREA issue with valid normalized area name")
        void createCommonAreaIssueSuccess() {
            // Arrange
            CreateIssueRequestDto request = new CreateIssueRequestDto(
                    IssueLocationType.COMMON_AREA,
                    "  PRALNIA  ",
                    IssueCategory.ELECTRICAL,
                    IssueUrgency.NORMAL,
                    "Spalony bezpiecznik"
            );

            when(issueRepository.saveAndFlush(any(Issue.class))).thenAnswer(inv -> {
                Issue i = inv.getArgument(0);
                i.setId(UUID.randomUUID());
                i.setCreatedAt(Instant.now());
                i.setUpdatedAt(Instant.now());
                return i;
            });

            // Act
            IssueDto result = issueService.createIssue(resident, request, null);

            // Assert
            assertNotNull(result);
            assertEquals("Pralnia", result.locationLabel());
            assertEquals("pralnia", result.commonAreaName());
            assertNull(result.roomId());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when commonAreaName is invalid")
        void createCommonAreaThrowsOnInvalidArea() {
            // Arrange
            CreateIssueRequestDto request = new CreateIssueRequestDto(
                    IssueLocationType.COMMON_AREA,
                    "basen",
                    IssueCategory.OTHER,
                    IssueUrgency.NORMAL,
                    "Opis"
            );

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> issueService.createIssue(resident, request, null));
            assertTrue(ex.getMessage().contains("commonAreaName must be one of"));
        }

        @Test
        @DisplayName("Should upload photo, associate with issue, and clean up if save fails")
        void createIssueWithPhotoSuccessAndCleanup() {
            // Arrange
            CreateIssueRequestDto request = new CreateIssueRequestDto(
                    IssueLocationType.COMMON_AREA,
                    "korytarz",
                    IssueCategory.OTHER,
                    IssueUrgency.NORMAL,
                    "Uszkodzona lampa"
            );

            MockMultipartFile photo = new MockMultipartFile(
                    "photo", "lampa.jpg", "image/jpeg", new byte[]{1, 2, 3}
            );

            when(minioStorageService.uploadIssuePhoto(photo)).thenReturn("issues/uploaded.jpg");
            when(issueRepository.saveAndFlush(any(Issue.class))).thenThrow(new RuntimeException("DB crash"));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> issueService.createIssue(resident, request, photo));
            verify(minioStorageService).uploadIssuePhoto(photo);
            verify(minioStorageService).removeIssuePhoto("issues/uploaded.jpg");
        }
    }

    @Nested
    @DisplayName("getOwnIssuePhotoPresignedUrl")
    class GetOwnIssuePhotoPresignedUrl {

        @Test
        @DisplayName("Should return presigned URL when photo exists")
        void getPhotoPresignedUrlSuccess() {
            // Arrange
            IssuePhoto photo = IssuePhoto.builder()
                    .photoUrl("issues/myphoto.jpg")
                    .build();
            issue.addPhoto(photo);

            when(issueRepository.findByIdAndReporterIdWithDetails(issueId, resident.getId()))
                    .thenReturn(Optional.of(issue));
            when(minioStorageService.getIssuePresignedUrl("issues/myphoto.jpg", 30))
                    .thenReturn("https://minio/myphoto.jpg");

            // Act
            String url = issueService.getOwnIssuePhotoPresignedUrl(resident, issueId);

            // Assert
            assertEquals("https://minio/myphoto.jpg", url);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when issue has no photo")
        void getPhotoThrowsWhenNoPhoto() {
            // Arrange
            when(issueRepository.findByIdAndReporterIdWithDetails(issueId, resident.getId()))
                    .thenReturn(Optional.of(issue));

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> issueService.getOwnIssuePhotoPresignedUrl(resident, issueId));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when issue not found")
        void getPhotoThrowsWhenNotFound() {
            // Arrange
            UUID missingId = UUID.randomUUID();
            when(issueRepository.findByIdAndReporterIdWithDetails(missingId, resident.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> issueService.getOwnIssuePhotoPresignedUrl(resident, missingId));
        }
    }

    @Nested
    @DisplayName("updateStaffIssueStatus")
    class UpdateStaffIssueStatus {

        @Test
        @DisplayName("Should transition from NEW to ASSIGNED_TO_MAINTENANCE and send email")
        void updateStatusToAssignedSuccess() {
            // Arrange
            when(issueRepository.findByIdAndDormitoryIdWithDetails(issueId, dorm1.getId()))
                    .thenReturn(Optional.of(issue));
            when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            StaffIssueDto result = issueService.updateStaffIssueStatus(
                    dorm1.getId(), issueId, IssueStatus.ASSIGNED_TO_MAINTENANCE, "Przydzielono hydraulika"
            );

            // Assert
            assertEquals(IssueStatus.ASSIGNED_TO_MAINTENANCE, result.status());
            assertEquals("Przydzielono hydraulika", result.staffNotes());
            verify(emailService).sendIssueStatusChangedEmail(
                    eq(resident.getEmail()), eq(resident.getFirstName()), eq("ASSIGNED_TO_MAINTENANCE"), eq("Przydzielono hydraulika")
            );
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when rejecting without staffNotes")
        void updateStatusToRejectedRequiresNotes() {
            // Arrange
            when(issueRepository.findByIdAndDormitoryIdWithDetails(issueId, dorm1.getId()))
                    .thenReturn(Optional.of(issue));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> issueService.updateStaffIssueStatus(dorm1.getId(), issueId, IssueStatus.REJECTED, "   "));
            assertTrue(ex.getMessage().contains("staffNotes is required when rejecting"));
            verify(issueRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException on illegal status transition")
        void updateStatusThrowsOnIllegalTransition() {
            // Arrange
            when(issueRepository.findByIdAndDormitoryIdWithDetails(issueId, dorm1.getId()))
                    .thenReturn(Optional.of(issue));

            // Act & Assert (NEW -> RESOLVED is illegal)
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> issueService.updateStaffIssueStatus(dorm1.getId(), issueId, IssueStatus.RESOLVED, null));
            assertTrue(ex.getMessage().contains("Illegal status transition"));
            verify(issueRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when transitioning to same status")
        void updateStatusThrowsOnSameStatus() {
            // Arrange
            when(issueRepository.findByIdAndDormitoryIdWithDetails(issueId, dorm1.getId()))
                    .thenReturn(Optional.of(issue));

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> issueService.updateStaffIssueStatus(dorm1.getId(), issueId, IssueStatus.NEW, null));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when issue not found in dormitory")
        void updateStatusThrowsWhenNotFound() {
            // Arrange
            UUID notFoundId = UUID.randomUUID();
            when(issueRepository.findByIdAndDormitoryIdWithDetails(notFoundId, dorm1.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> issueService.updateStaffIssueStatus(dorm1.getId(), notFoundId, IssueStatus.ASSIGNED_TO_MAINTENANCE, null));
        }
    }
}
