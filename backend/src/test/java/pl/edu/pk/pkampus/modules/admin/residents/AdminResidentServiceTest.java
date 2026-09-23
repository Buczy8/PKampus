package pl.edu.pk.pkampus.modules.admin.residents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.FileStorageException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentResponseDto;
import pl.edu.pk.pkampus.modules.admin.dto.PendingResidentDto;
import pl.edu.pk.pkampus.modules.admin.dto.RejectResidentRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Clock;
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
@DisplayName("AdminResidentService unit tests")
class AdminResidentServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private Clock clock;

    @InjectMocks
    private AdminResidentService adminResidentService;

    private Dormitory dormitory;
    private Dormitory otherDormitory;
    private User dormAdmin;
    private User pendingResident;
    private Room room;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        dormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Akademik")
                .code("DS1")
                .build();
        otherDormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("Inny DS")
                .code("DS2")
                .build();

        dormAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("kierownik@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .build();

        pendingResident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48111111111")
                .avatarUrl("avatar.jpg")
                .role(UserRole.RESIDENT)
                .status(UserStatus.PENDING_APPROVAL)
                .dormitory(dormitory)
                .declaredRoomNumber("101")
                .build();

        room = Room.builder()
                .id(UUID.randomUUID())
                .dormitory(dormitory)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build();
    }

    @Nested
    @DisplayName("listPendingResidents")
    class ListPending {

        @Test
        void returnsPendingResidentsWithPresignedAvatar() {
            when(userRepository.findAllByDormitoryIdAndStatus(dormitory.getId(), UserStatus.PENDING_APPROVAL))
                    .thenReturn(List.of(pendingResident));
            when(minioStorageService.getAvatarPresignedUrl("avatar.jpg", 60))
                    .thenReturn("https://minio/avatar.jpg");

            List<PendingResidentDto> result = adminResidentService.listPendingResidents(dormAdmin);

            assertEquals(1, result.size());
            PendingResidentDto dto = result.getFirst();
            assertEquals(pendingResident.getId(), dto.getId());
            assertEquals("student@pk.edu.pl", dto.getEmail());
            assertEquals("Jan", dto.getFirstName());
            assertEquals("Kowalski", dto.getLastName());
            assertEquals("101", dto.getDeclaredRoomNumber());
            assertEquals(dormitory.getId(), dto.getDormitoryId());
            assertEquals("DS Akademik", dto.getDormitoryName());
            assertEquals("https://minio/avatar.jpg", dto.getAvatarUrl());
        }

        @Test
        void filtersOutNonResidentRoles() {
            User pendingReceptionist = User.builder()
                    .id(UUID.randomUUID())
                    .email("portier@pk.edu.pl")
                    .role(UserRole.RECEPTIONIST)
                    .status(UserStatus.PENDING_APPROVAL)
                    .dormitory(dormitory)
                    .build();
            when(userRepository.findAllByDormitoryIdAndStatus(dormitory.getId(), UserStatus.PENDING_APPROVAL))
                    .thenReturn(List.of(pendingResident, pendingReceptionist));

            List<PendingResidentDto> result = adminResidentService.listPendingResidents(dormAdmin);

            assertEquals(1, result.size());
            assertEquals(pendingResident.getId(), result.getFirst().getId());
        }

        @Test
        void returnsEmptyListWhenNoPending() {
            when(userRepository.findAllByDormitoryIdAndStatus(dormitory.getId(), UserStatus.PENDING_APPROVAL))
                    .thenReturn(List.of());

            assertTrue(adminResidentService.listPendingResidents(dormAdmin).isEmpty());
        }

        @Test
        void continuesWhenAvatarPresignFails() {
            when(userRepository.findAllByDormitoryIdAndStatus(dormitory.getId(), UserStatus.PENDING_APPROVAL))
                    .thenReturn(List.of(pendingResident));
            when(minioStorageService.getAvatarPresignedUrl(anyString(), anyInt()))
                    .thenThrow(new FileStorageException("minio down"));

            List<PendingResidentDto> result = adminResidentService.listPendingResidents(dormAdmin);

            assertEquals(1, result.size());
            assertNull(result.getFirst().getAvatarUrl());
        }

        @Test
        void allowsBlankAvatarWithoutCallingMinio() {
            pendingResident.setAvatarUrl("  ");
            when(userRepository.findAllByDormitoryIdAndStatus(dormitory.getId(), UserStatus.PENDING_APPROVAL))
                    .thenReturn(List.of(pendingResident));

            List<PendingResidentDto> result = adminResidentService.listPendingResidents(dormAdmin);

            assertNull(result.getFirst().getAvatarUrl());
            verify(minioStorageService, never()).getAvatarPresignedUrl(anyString(), anyInt());
        }

        @Test
        void superAdminListsPendingAcrossAllDormitories() {
            User superAdmin = User.builder()
                    .id(UUID.randomUUID())
                    .email("super@pk.edu.pl")
                    .role(UserRole.SUPER_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .dormitory(null)
                    .build();
            when(userRepository.findAllByStatus(UserStatus.PENDING_APPROVAL))
                    .thenReturn(List.of(pendingResident));

            assertEquals(1, adminResidentService.listPendingResidents(superAdmin).size());
            verify(userRepository).findAllByStatus(UserStatus.PENDING_APPROVAL);
            verify(userRepository, never()).findAllByDormitoryIdAndStatus(any(), any());
        }

        @Test
        void receptionistCannotList() {
            User receptionist = User.builder()
                    .id(UUID.randomUUID())
                    .email("portier@pk.edu.pl")
                    .role(UserRole.RECEPTIONIST)
                    .status(UserStatus.ACTIVE)
                    .dormitory(dormitory)
                    .build();

            assertThrows(AccessDeniedException.class,
                    () -> adminResidentService.listPendingResidents(receptionist));
        }

        @Test
        void dormAdminWithoutDormitoryThrows() {
            dormAdmin.setDormitory(null);
            assertThrows(BusinessRuleException.class,
                    () -> adminResidentService.listPendingResidents(dormAdmin));
        }
    }

    @Nested
    @DisplayName("activateResident")
    class Activate {

        private void stubSuccessfulActivate(String roomNumber, Room targetRoom) {
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
            when(roomRepository.findByDormitoryIdAndRoomNumber(dormitory.getId(), roomNumber))
                    .thenReturn(Optional.of(targetRoom));
            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(pendingResident.getId()))
                    .thenReturn(Optional.empty());
            when(roomAssignmentRepository.save(any(RoomAssignment.class))).thenAnswer(inv -> {
                RoomAssignment ra = inv.getArgument(0);
                ra.setId(UUID.randomUUID());
                return ra;
            });
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void activatesWithDeclaredRoomAndSendsEmail() {
            stubSuccessfulActivate("101", room);

            ActivateResidentResponseDto response = adminResidentService.activateResident(
                    dormAdmin, pendingResident.getId(), new ActivateResidentRequestDto());

            assertEquals(UserStatus.ACTIVE, pendingResident.getStatus());
            assertEquals(UserStatus.ACTIVE, response.getStatus());
            assertEquals("101", response.getRoomNumber());
            assertNotNull(response.getRoomAssignmentId());
            assertNotNull(response.getAcademicYear());
            assertTrue(response.getAcademicYear().matches("\\d{4}/\\d{4}"));
            ArgumentCaptor<ResidentActivatedEvent> eventCaptor = ArgumentCaptor.forClass(ResidentActivatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(pendingResident.getId(), eventCaptor.getValue().userId());
            assertEquals("student@pk.edu.pl", eventCaptor.getValue().email());
            assertEquals("101", eventCaptor.getValue().roomNumber());
            assertEquals("DS Akademik", eventCaptor.getValue().dormitoryName());
        }

        @Test
        void activatesWithNullRequestBody() {
            stubSuccessfulActivate("101", room);

            ActivateResidentResponseDto response = adminResidentService.activateResident(
                    dormAdmin, pendingResident.getId(), null);

            assertEquals("101", response.getRoomNumber());
        }

        @Test
        void activatesWithRoomOverride() {
            Room room202 = Room.builder()
                    .id(UUID.randomUUID())
                    .dormitory(dormitory)
                    .roomNumber("202")
                    .floor(2)
                    .capacity(2)
                    .build();
            stubSuccessfulActivate("202", room202);

            ActivateResidentResponseDto response = adminResidentService.activateResident(
                    dormAdmin,
                    pendingResident.getId(),
                    ActivateResidentRequestDto.builder().roomNumber("202").build());

            assertEquals("202", response.getRoomNumber());
            assertEquals("202", pendingResident.getDeclaredRoomNumber());
            ArgumentCaptor<RoomAssignment> captor = ArgumentCaptor.forClass(RoomAssignment.class);
            verify(roomAssignmentRepository).save(captor.capture());
            assertEquals("202", captor.getValue().getRoom().getRoomNumber());
            assertEquals(true, captor.getValue().getIsActive());
            assertNotNull(captor.getValue().getCheckInDate());
        }

        @Test
        void trimsRoomOverrideWhitespace() {
            Room room202 = Room.builder()
                    .id(UUID.randomUUID())
                    .dormitory(dormitory)
                    .roomNumber("202")
                    .floor(2)
                    .capacity(2)
                    .build();
            stubSuccessfulActivate("202", room202);

            adminResidentService.activateResident(
                    dormAdmin,
                    pendingResident.getId(),
                    ActivateResidentRequestDto.builder().roomNumber("  202  ").build());

            verify(roomRepository).findByDormitoryIdAndRoomNumber(dormitory.getId(), "202");
        }

        @Test
        void failsWhenRoomMissing() {
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
            when(roomRepository.findByDormitoryIdAndRoomNumber(dormitory.getId(), "101"))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
            verify(roomAssignmentRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void failsWhenDeclaredRoomBlankAndNoOverride() {
            pendingResident.setDeclaredRoomNumber("  ");
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            assertThrows(BusinessRuleException.class, () ->
                    adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
        }

        @Test
        void failsWhenActiveAssignmentAlreadyExists() {
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
            when(roomRepository.findByDormitoryIdAndRoomNumber(dormitory.getId(), "101"))
                    .thenReturn(Optional.of(room));
            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(pendingResident.getId()))
                    .thenReturn(Optional.of(RoomAssignment.builder().id(UUID.randomUUID()).build()));

            assertThrows(AccountStatusException.class, () ->
                    adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
            verify(roomAssignmentRepository, never()).save(any());
        }

        @Test
        void failsWhenResidentNotFound() {
            UUID missingId = UUID.randomUUID();
            when(userRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    adminResidentService.activateResident(dormAdmin, missingId, null));
        }

        @Test
        void failsWhenStatusNotPendingApproval() {
            for (UserStatus status : List.of(
                    UserStatus.PENDING_EMAIL, UserStatus.ACTIVE, UserStatus.BLOCKED, UserStatus.CHECKED_OUT)) {
                pendingResident.setStatus(status);
                when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

                AccountStatusException ex = assertThrows(AccountStatusException.class, () ->
                        adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
                assertTrue(ex.getMessage().contains("not awaiting approval"));
            }
        }

        @Test
        void failsWhenTargetIsNotResident() {
            pendingResident.setRole(UserRole.RECEPTIONIST);
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            assertThrows(AccountStatusException.class, () ->
                    adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
        }

        @Test
        void failsWhenResidentHasNoDormitory() {
            pendingResident.setDormitory(null);
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            assertThrows(AccountStatusException.class, () ->
                    adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
        }

        @Test
        void failsWhenResidentFromOtherDormitory() {
            pendingResident.setDormitory(otherDormitory);
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            assertThrows(AccessDeniedException.class, () ->
                    adminResidentService.activateResident(dormAdmin, pendingResident.getId(), null));
        }
    }

    @Nested
    @DisplayName("rejectResident")
    class Reject {

        @Test
        void deletesUserRemovesAvatarAndEmails() {
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            adminResidentService.rejectResident(
                    dormAdmin,
                    pendingResident.getId(),
                    new RejectResidentRequestDto("  Not on housing list  "));

            verify(userRepository).delete(pendingResident);
            verify(userRepository).flush();
            verify(minioStorageService).removeAvatar("avatar.jpg");
            ArgumentCaptor<RegistrationRejectedEvent> eventCaptor =
                    ArgumentCaptor.forClass(RegistrationRejectedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(pendingResident.getId(), eventCaptor.getValue().residentId());
            assertEquals("student@pk.edu.pl", eventCaptor.getValue().email());
            assertEquals("Not on housing list", eventCaptor.getValue().reason());
        }

        @Test
        void stillEmailsWhenAvatarRemovalFails() {
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
            doThrow(new FileStorageException("minio error")).when(minioStorageService).removeAvatar("avatar.jpg");

            assertDoesNotThrow(() -> adminResidentService.rejectResident(
                    dormAdmin,
                    pendingResident.getId(),
                    new RejectResidentRequestDto("Rejected")));

            verify(eventPublisher).publishEvent(any(RegistrationRejectedEvent.class));
        }

        @Test
        void skipsAvatarRemovalWhenMissing() {
            pendingResident.setAvatarUrl(null);
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            adminResidentService.rejectResident(
                    dormAdmin,
                    pendingResident.getId(),
                    new RejectResidentRequestDto("Rejected"));

            verify(minioStorageService, never()).removeAvatar(any());
            verify(eventPublisher).publishEvent(any(RegistrationRejectedEvent.class));
        }

        @Test
        void failsOutsideScope() {
            pendingResident.setDormitory(otherDormitory);
            when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

            assertThrows(AccessDeniedException.class, () ->
                    adminResidentService.rejectResident(
                            dormAdmin,
                            pendingResident.getId(),
                            new RejectResidentRequestDto("Rejected")));
            verify(userRepository, never()).delete(any());
        }
    }
}
