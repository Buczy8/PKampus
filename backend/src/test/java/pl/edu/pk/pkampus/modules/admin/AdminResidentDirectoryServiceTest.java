package pl.edu.pk.pkampus.modules.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.FileStorageException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.admin.dto.CreateRoomBanRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ManagedResidentDto;
import pl.edu.pk.pkampus.modules.admin.dto.SanctionDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
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
@DisplayName("AdminResidentDirectoryService unit tests (AAA)")
class AdminResidentDirectoryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private SanctionRepository sanctionRepository;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private EmailService emailService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private Clock clock;

    @InjectMocks
    private AdminResidentDirectoryService adminResidentDirectoryService;

    private Dormitory dormitory;
    private User admin;
    private User resident;
    private UUID residentId;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        dormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Directory Test")
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .dormitory(dormitory)
                .build();

        residentId = UUID.randomUUID();
        resident = User.builder()
                .id(residentId)
                .email("resident@pk.edu.pl")
                .firstName("Piotr")
                .lastName("Zieliński")
                .phoneNumber("+48123456789")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .declaredRoomNumber("105")
                .avatarUrl("avatar-piotr.jpg")
                .build();
    }

    @Nested
    @DisplayName("listResidents")
    class ListResidents {

        @Test
        @DisplayName("Should return residents list with active room assignments and sanctions")
        void listResidentsSuccess() {
            // Arrange
            Room room = Room.builder().roomNumber("105-B").build();
            RoomAssignment assignment = RoomAssignment.builder().user(resident).room(room).isActive(true).build();
            Sanction sanction = Sanction.builder()
                    .id(UUID.randomUUID())
                    .user(resident)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now().plusMonths(1))
                    .reason("Quiet hours violation")
                    .active(true)
                    .build();

            when(userRepository.findAllByDormitoryIdAndRoleAndStatusInOrderByLastNameAscFirstNameAsc(
                    eq(dormitory.getId()), eq(UserRole.RESIDENT), eq(EnumSet.of(UserStatus.ACTIVE, UserStatus.BLOCKED))))
                    .thenReturn(List.of(resident));
            when(sanctionRepository.findActiveRoomBansForUsers(eq(List.of(residentId)), any(LocalDate.class)))
                    .thenReturn(List.of(sanction));
            when(roomAssignmentRepository.findActiveByUserIdIn(eq(List.of(residentId))))
                    .thenReturn(List.of(assignment));
            when(minioStorageService.getAvatarPresignedUrl("avatar-piotr.jpg", 60))
                    .thenReturn("https://minio/avatar-piotr.jpg");

            // Act
            List<ManagedResidentDto> result = adminResidentDirectoryService.listResidents(admin);

            // Assert
            assertEquals(1, result.size());
            ManagedResidentDto dto = result.getFirst();
            assertEquals(residentId, dto.getId());
            assertEquals("resident@pk.edu.pl", dto.getEmail());
            assertEquals("105-B", dto.getRoomNumber());
            assertEquals("https://minio/avatar-piotr.jpg", dto.getAvatarUrl());
            assertNotNull(dto.getActiveRoomBan());
            assertEquals("Quiet hours violation", dto.getActiveRoomBan().getReason());
        }

        @Test
        @DisplayName("Should use declared room number when active room assignment is absent")
        void listResidentsFallsBackToDeclaredRoom() {
            // Arrange
            when(userRepository.findAllByDormitoryIdAndRoleAndStatusInOrderByLastNameAscFirstNameAsc(
                    any(), any(), any()))
                    .thenReturn(List.of(resident));
            when(sanctionRepository.findActiveRoomBansForUsers(any(), any()))
                    .thenReturn(List.of());
            when(roomAssignmentRepository.findActiveByUserIdIn(any()))
                    .thenReturn(List.of());

            // Act
            List<ManagedResidentDto> result = adminResidentDirectoryService.listResidents(admin);

            // Assert
            assertEquals(1, result.size());
            assertEquals("105", result.getFirst().getRoomNumber());
        }

        @Test
        @DisplayName("Should return empty list without querying sanctions when no residents")
        void listResidentsEmpty() {
            // Arrange
            when(userRepository.findAllByDormitoryIdAndRoleAndStatusInOrderByLastNameAscFirstNameAsc(
                    any(), any(), any()))
                    .thenReturn(List.of());

            // Act
            List<ManagedResidentDto> result = adminResidentDirectoryService.listResidents(admin);

            // Assert
            assertTrue(result.isEmpty());
            verify(sanctionRepository, never()).findActiveRoomBansForUsers(any(), any());
            verify(roomAssignmentRepository, never()).findActiveByUserIdIn(any());
        }

        @Test
        @DisplayName("Should handle minio exception gracefully and set avatarUrl to null")
        void listResidentsMinioException() {
            // Arrange
            when(userRepository.findAllByDormitoryIdAndRoleAndStatusInOrderByLastNameAscFirstNameAsc(
                    any(), any(), any()))
                    .thenReturn(List.of(resident));
            when(sanctionRepository.findActiveRoomBansForUsers(any(), any()))
                    .thenReturn(List.of());
            when(roomAssignmentRepository.findActiveByUserIdIn(any()))
                    .thenReturn(List.of());
            when(minioStorageService.getAvatarPresignedUrl(anyString(), anyInt()))
                    .thenThrow(new FileStorageException("Minio unavailable"));

            // Act
            List<ManagedResidentDto> result = adminResidentDirectoryService.listResidents(admin);

            // Assert
            assertEquals(1, result.size());
            assertNull(result.getFirst().getAvatarUrl());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when caller is not DORM_ADMIN")
        void listResidentsThrowsWhenNotDormAdmin() {
            // Arrange
            User nonAdmin = User.builder().role(UserRole.RESIDENT).dormitory(dormitory).build();

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> adminResidentDirectoryService.listResidents(nonAdmin));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when admin has no dormitory")
        void listResidentsThrowsWhenAdminHasNoDormitory() {
            // Arrange
            User noDormAdmin = User.builder().role(UserRole.DORM_ADMIN).dormitory(null).build();

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> adminResidentDirectoryService.listResidents(noDormAdmin));
        }
    }

    @Nested
    @DisplayName("block")
    class BlockResident {

        @Test
        @DisplayName("Should block ACTIVE resident, revoke tokens, and send notification email")
        void blockSuccess() {
            // Arrange
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ManagedResidentDto result = adminResidentDirectoryService.block(admin, residentId);

            // Assert
            assertEquals(UserStatus.BLOCKED, resident.getStatus());
            assertEquals(UserStatus.BLOCKED, result.getStatus());
            verify(tokenRevocationService).revokeUser(residentId);
            verify(emailService).sendAccountBlockedEmail(eq("resident@pk.edu.pl"), eq("Piotr"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when resident is already blocked")
        void blockThrowsWhenAlreadyBlocked() {
            // Arrange
            resident.setStatus(UserStatus.BLOCKED);
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminResidentDirectoryService.block(admin, residentId));
            assertEquals("Resident is already blocked", ex.getMessage());
            verify(tokenRevocationService, never()).revokeUser(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when resident not found in dormitory")
        void blockThrowsWhenNotFound() {
            // Arrange
            UUID notFoundId = UUID.randomUUID();
            when(userRepository.findByIdAndDormitoryIdAndRole(notFoundId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> adminResidentDirectoryService.block(admin, notFoundId));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when resident status is not manageable (e.g. PENDING_APPROVAL)")
        void blockThrowsWhenStatusNotManageable() {
            // Arrange
            resident.setStatus(UserStatus.PENDING_APPROVAL);
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> adminResidentDirectoryService.block(admin, residentId));
        }
    }

    @Nested
    @DisplayName("unblock")
    class UnblockResident {

        @Test
        @DisplayName("Should unblock BLOCKED resident and clear revocation")
        void unblockSuccess() {
            // Arrange
            resident.setStatus(UserStatus.BLOCKED);
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ManagedResidentDto result = adminResidentDirectoryService.unblock(admin, residentId);

            // Assert
            assertEquals(UserStatus.ACTIVE, resident.getStatus());
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            verify(tokenRevocationService).clearRevocation(residentId);
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when resident is not blocked")
        void unblockThrowsWhenNotBlocked() {
            // Arrange
            resident.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminResidentDirectoryService.unblock(admin, residentId));
            assertEquals("Resident is not blocked", ex.getMessage());
            verify(tokenRevocationService, never()).clearRevocation(any());
        }
    }

    @Nested
    @DisplayName("checkout")
    class CheckoutResident {

        @Test
        @DisplayName("Should close room assignment, clear and delete avatar, revoke tokens, and email resident")
        void checkoutSuccess() {
            // Arrange
            RoomAssignment activeAssignment = RoomAssignment.builder()
                    .id(UUID.randomUUID())
                    .user(resident)
                    .isActive(true)
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(residentId))
                    .thenReturn(Optional.of(activeAssignment));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ManagedResidentDto result = adminResidentDirectoryService.checkout(admin, residentId);

            // Assert
            assertFalse(activeAssignment.getIsActive());
            assertEquals(LocalDate.now(), activeAssignment.getCheckOutDate());
            verify(roomAssignmentRepository).save(activeAssignment);

            assertEquals(UserStatus.CHECKED_OUT, resident.getStatus());
            assertNull(resident.getAvatarUrl());
            assertEquals(UserStatus.CHECKED_OUT, result.getStatus());

            verify(minioStorageService).removeAvatar("avatar-piotr.jpg");
            verify(tokenRevocationService).revokeUser(residentId);
            verify(emailService).sendCheckedOutEmail(eq("resident@pk.edu.pl"), eq("Piotr"));
        }

        @Test
        @DisplayName("Should still succeed checkout if avatar removal fails")
        void checkoutContinuesWhenAvatarRemovalFails() {
            // Arrange
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(residentId))
                    .thenReturn(Optional.empty());
            doThrow(new FileStorageException("Minio delete error")).when(minioStorageService).removeAvatar(anyString());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ManagedResidentDto result = adminResidentDirectoryService.checkout(admin, residentId);

            // Assert
            assertEquals(UserStatus.CHECKED_OUT, result.getStatus());
            verify(emailService).sendCheckedOutEmail(eq("resident@pk.edu.pl"), eq("Piotr"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException if resident is already checked out")
        void checkoutThrowsWhenAlreadyCheckedOut() {
            // Arrange
            resident.setStatus(UserStatus.CHECKED_OUT);
            // Note: loadManageableResident checks DIRECTORY_STATUSES (ACTIVE, BLOCKED). CHECKED_OUT fails the check.
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> adminResidentDirectoryService.checkout(admin, residentId));
        }
    }

    @Nested
    @DisplayName("issueRoomBan")
    class IssueRoomBan {

        @Test
        @DisplayName("Should issue room ban for specified months, save sanction, and send email")
        void issueRoomBanSuccess() {
            // Arrange
            CreateRoomBanRequestDto request = CreateRoomBanRequestDto.builder()
                    .durationMonths(2)
                    .reason("   Damage to kitchen amenities   ")
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(sanctionRepository.findActiveByUserAndType(eq(residentId), eq(SanctionType.ROOM_BAN), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(sanctionRepository.save(any(Sanction.class))).thenAnswer(inv -> {
                Sanction s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

            // Act
            SanctionDto result = adminResidentDirectoryService.issueRoomBan(admin, residentId, request);

            // Assert
            assertNotNull(result);
            assertEquals(SanctionType.ROOM_BAN, result.getSanctionType());
            assertEquals("Damage to kitchen amenities", result.getReason());
            assertTrue(result.isActive());
            assertEquals(LocalDate.now(), result.getStartDate());
            assertEquals(LocalDate.now().plusMonths(2), result.getEndDate());

            ArgumentCaptor<Sanction> captor = ArgumentCaptor.forClass(Sanction.class);
            verify(sanctionRepository).save(captor.capture());
            Sanction saved = captor.getValue();
            assertEquals(resident, saved.getUser());
            assertEquals(admin, saved.getIssuedBy());
            assertEquals(dormitory, saved.getDormitory());

            verify(emailService).sendRoomBanEmail(eq("resident@pk.edu.pl"), eq("Piotr"),
                    any(LocalDate.class), any(LocalDate.class), eq("Damage to kitchen amenities"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when resident already has an active room ban")
        void issueRoomBanThrowsWhenAlreadyBanned() {
            // Arrange
            CreateRoomBanRequestDto request = CreateRoomBanRequestDto.builder()
                    .durationMonths(1)
                    .reason("Test")
                    .build();
            Sanction existing = Sanction.builder().id(UUID.randomUUID()).build();

            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(sanctionRepository.findActiveByUserAndType(eq(residentId), eq(SanctionType.ROOM_BAN), any(LocalDate.class)))
                    .thenReturn(List.of(existing));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminResidentDirectoryService.issueRoomBan(admin, residentId, request));
            assertEquals("Resident already has an active ROOM_BAN", ex.getMessage());
            verify(sanctionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeRoomBan")
    class RevokeRoomBan {

        @Test
        @DisplayName("Should revoke active ROOM_BAN successfully")
        void revokeRoomBanSuccess() {
            // Arrange
            UUID sanctionId = UUID.randomUUID();
            Sanction activeSanction = Sanction.builder()
                    .id(sanctionId)
                    .user(resident)
                    .sanctionType(SanctionType.ROOM_BAN)
                    .active(true)
                    .startDate(LocalDate.now().minusDays(5))
                    .endDate(LocalDate.now().plusDays(25))
                    .reason("Violation")
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(sanctionRepository.findByIdAndUserId(sanctionId, residentId))
                    .thenReturn(Optional.of(activeSanction));
            when(sanctionRepository.save(any(Sanction.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            SanctionDto result = adminResidentDirectoryService.revokeRoomBan(admin, residentId, sanctionId);

            // Assert
            assertFalse(result.isActive());
            assertFalse(activeSanction.isActive());
            verify(sanctionRepository).save(activeSanction);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when sanction not found")
        void revokeRoomBanThrowsWhenNotFound() {
            // Arrange
            UUID missingSanctionId = UUID.randomUUID();
            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(sanctionRepository.findByIdAndUserId(missingSanctionId, residentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> adminResidentDirectoryService.revokeRoomBan(admin, residentId, missingSanctionId));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when sanction is already inactive")
        void revokeRoomBanThrowsWhenAlreadyInactive() {
            // Arrange
            UUID sanctionId = UUID.randomUUID();
            Sanction inactiveSanction = Sanction.builder()
                    .id(sanctionId)
                    .user(resident)
                    .sanctionType(SanctionType.ROOM_BAN)
                    .active(false)
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(residentId, dormitory.getId(), UserRole.RESIDENT))
                    .thenReturn(Optional.of(resident));
            when(sanctionRepository.findByIdAndUserId(sanctionId, residentId))
                    .thenReturn(Optional.of(inactiveSanction));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminResidentDirectoryService.revokeRoomBan(admin, residentId, sanctionId));
            assertEquals("Sanction is already inactive", ex.getMessage());
        }
    }
}
