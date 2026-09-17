package pl.edu.pk.pkampus.modules.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    private EmailService emailService;

    @InjectMocks
    private AdminResidentService adminResidentService;

    private Dormitory dormitory;
    private User admin;
    private User pendingResident;
    private Room room;

    @BeforeEach
    void setUp() {
        UUID dormId = UUID.randomUUID();
        dormitory = Dormitory.builder()
                .id(dormId)
                .name("DS Akademik")
                .code("DS1")
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
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

    @Test
    void shouldListPendingResidentsWithPresignedAvatar() {
        when(userRepository.findAllByDormitoryIdAndStatus(dormitory.getId(), UserStatus.PENDING_APPROVAL))
                .thenReturn(List.of(pendingResident));
        when(minioStorageService.getAvatarPresignedUrl("avatar.jpg", 60))
                .thenReturn("https://minio.local/avatar.jpg?sig=1");

        List<PendingResidentDto> result = adminResidentService.listPendingResidents(admin);

        assertEquals(1, result.size());
        assertEquals(pendingResident.getId(), result.getFirst().getId());
        assertEquals("101", result.getFirst().getDeclaredRoomNumber());
        assertEquals("https://minio.local/avatar.jpg?sig=1", result.getFirst().getAvatarUrl());
    }

    @Test
    void shouldActivateResidentAndCreateAssignment() {
        when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
        when(roomRepository.findByDormitoryIdAndRoomNumber(dormitory.getId(), "101"))
                .thenReturn(Optional.of(room));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(pendingResident.getId()))
                .thenReturn(Optional.empty());
        when(roomAssignmentRepository.save(any(RoomAssignment.class))).thenAnswer(inv -> {
            RoomAssignment ra = inv.getArgument(0);
            ra.setId(UUID.randomUUID());
            return ra;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivateResidentResponseDto response = adminResidentService.activateResident(
                admin,
                pendingResident.getId(),
                new ActivateResidentRequestDto()
        );

        assertEquals(UserStatus.ACTIVE, pendingResident.getStatus());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertEquals("101", response.getRoomNumber());
        assertNotNull(response.getAcademicYear());
        verify(emailService).sendAccountActivatedEmail(
                eq("student@pk.edu.pl"),
                eq("Jan"),
                eq("101"),
                eq("DS Akademik")
        );
    }

    @Test
    void shouldRejectWhenRoomMissing() {
        when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
        when(roomRepository.findByDormitoryIdAndRoomNumber(dormitory.getId(), "101"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                adminResidentService.activateResident(admin, pendingResident.getId(), null));
    }

    @Test
    void shouldRejectResidentDeleteAvatarAndEmail() {
        when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

        adminResidentService.rejectResident(
                admin,
                pendingResident.getId(),
                new RejectResidentRequestDto("Not on housing list")
        );

        verify(userRepository).delete(pendingResident);
        verify(minioStorageService).removeAvatar("avatar.jpg");
        verify(emailService).sendRegistrationRejectedEmail(
                "student@pk.edu.pl",
                "Jan",
                "Not on housing list"
        );
    }

    @Test
    void shouldDenyActivateOutsideAdminDormitory() {
        Dormitory other = Dormitory.builder().id(UUID.randomUUID()).name("Other").code("X").build();
        pendingResident.setDormitory(other);
        when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

        assertThrows(AccessDeniedException.class, () ->
                adminResidentService.activateResident(admin, pendingResident.getId(), null));
    }

    @Test
    void shouldRejectNonPendingStatus() {
        pendingResident.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));

        assertThrows(AccountStatusException.class, () ->
                adminResidentService.activateResident(admin, pendingResident.getId(), null));
    }

    @Test
    void shouldUseRoomNumberOverrideOnActivate() {
        when(userRepository.findById(pendingResident.getId())).thenReturn(Optional.of(pendingResident));
        Room room202 = Room.builder()
                .id(UUID.randomUUID())
                .dormitory(dormitory)
                .roomNumber("202")
                .floor(2)
                .capacity(2)
                .build();
        when(roomRepository.findByDormitoryIdAndRoomNumber(dormitory.getId(), "202"))
                .thenReturn(Optional.of(room202));
        when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(pendingResident.getId()))
                .thenReturn(Optional.empty());
        when(roomAssignmentRepository.save(any(RoomAssignment.class))).thenAnswer(inv -> {
            RoomAssignment ra = inv.getArgument(0);
            ra.setId(UUID.randomUUID());
            return ra;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivateResidentRequestDto request = ActivateResidentRequestDto.builder().roomNumber("202").build();
        ActivateResidentResponseDto response = adminResidentService.activateResident(
                admin, pendingResident.getId(), request);

        assertEquals("202", response.getRoomNumber());
        assertEquals("202", pendingResident.getDeclaredRoomNumber());

        ArgumentCaptor<RoomAssignment> captor = ArgumentCaptor.forClass(RoomAssignment.class);
        verify(roomAssignmentRepository).save(captor.capture());
        assertEquals("202", captor.getValue().getRoom().getRoomNumber());
    }
}
