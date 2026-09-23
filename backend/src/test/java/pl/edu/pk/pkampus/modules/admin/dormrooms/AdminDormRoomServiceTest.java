package pl.edu.pk.pkampus.modules.admin.dormrooms;

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
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.admin.dto.CreateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.DormRoomDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateDormRoomRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDormRoomService unit tests (AAA)")
class AdminDormRoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private AdminDormRoomService adminDormRoomService;

    private Dormitory dormitory;
    private User admin;
    private Room room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        dormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Room Test")
                .floorsCount(4)
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .dormitory(dormitory)
                .build();

        roomId = UUID.randomUUID();
        room = Room.builder()
                .id(roomId)
                .dormitory(dormitory)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build();
    }

    @Nested
    @DisplayName("list")
    class ListRooms {

        @Test
        @DisplayName("Should return list of rooms ordered by floor and room number")
        void listRoomsSuccess() {
            // Arrange
            when(roomRepository.findAllByDormitoryIdOrderByFloorAscRoomNumberAsc(dormitory.getId()))
                    .thenReturn(List.of(room));

            // Act
            List<DormRoomDto> result = adminDormRoomService.list(admin);

            // Assert
            assertEquals(1, result.size());
            DormRoomDto dto = result.getFirst();
            assertEquals(roomId, dto.getId());
            assertEquals("101", dto.getRoomNumber());
            assertEquals(1, dto.getFloor());
            assertEquals(2, dto.getCapacity());
            assertEquals(dormitory.getId(), dto.getDormitoryId());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when caller is not DORM_ADMIN")
        void listThrowsWhenNotDormAdmin() {
            // Arrange
            User resident = User.builder().role(UserRole.RESIDENT).dormitory(dormitory).build();

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> adminDormRoomService.list(resident));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when admin has no dormitory")
        void listThrowsWhenAdminHasNoDormitory() {
            // Arrange
            User noDormAdmin = User.builder().role(UserRole.DORM_ADMIN).dormitory(null).build();

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> adminDormRoomService.list(noDormAdmin));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateRoom {

        @Test
        @DisplayName("Should create room successfully when number is unique and floor within limit")
        void createRoomSuccess() {
            // Arrange
            CreateDormRoomRequestDto request = CreateDormRoomRequestDto.builder()
                    .roomNumber("  204  ")
                    .floor(2)
                    .capacity(3)
                    .build();

            when(roomRepository.existsByDormitoryIdAndRoomNumber(dormitory.getId(), "204")).thenReturn(false);
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
                Room r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });

            // Act
            DormRoomDto result = adminDormRoomService.create(admin, request);

            // Assert
            assertNotNull(result);
            assertEquals("204", result.getRoomNumber());
            assertEquals(2, result.getFloor());
            assertEquals(3, result.getCapacity());

            ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(captor.capture());
            Room saved = captor.getValue();
            assertEquals("204", saved.getRoomNumber());
            assertEquals(dormitory, saved.getDormitory());
            assertEquals(2, saved.getFloor());
            assertEquals(3, saved.getCapacity());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when room number already exists in dormitory")
        void createRoomThrowsWhenDuplicateNumber() {
            // Arrange
            CreateDormRoomRequestDto request = CreateDormRoomRequestDto.builder()
                    .roomNumber("101")
                    .floor(1)
                    .capacity(2)
                    .build();

            when(roomRepository.existsByDormitoryIdAndRoomNumber(dormitory.getId(), "101")).thenReturn(true);

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminDormRoomService.create(admin, request));
            assertEquals("A room with this number already exists in the dormitory", ex.getMessage());
            verify(roomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when floor exceeds dormitory floors count")
        void createRoomThrowsWhenFloorExceedsLimit() {
            // Arrange
            CreateDormRoomRequestDto request = CreateDormRoomRequestDto.builder()
                    .roomNumber("501")
                    .floor(5)
                    .capacity(2)
                    .build();

            when(roomRepository.existsByDormitoryIdAndRoomNumber(dormitory.getId(), "501")).thenReturn(false);

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminDormRoomService.create(admin, request));
            assertTrue(ex.getMessage().contains("Floor exceeds dormitory floors count"));
            verify(roomRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateRoom {

        @Test
        @DisplayName("Should update room fields successfully")
        void updateRoomSuccess() {
            // Arrange
            UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder()
                    .roomNumber("  101-A  ")
                    .floor(2)
                    .capacity(3)
                    .build();

            when(roomRepository.findByIdAndDormitoryId(roomId, dormitory.getId())).thenReturn(Optional.of(room));
            when(roomRepository.existsByDormitoryIdAndRoomNumberAndIdNot(dormitory.getId(), "101-A", roomId)).thenReturn(false);
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            DormRoomDto result = adminDormRoomService.update(admin, roomId, request);

            // Assert
            assertEquals("101-A", result.getRoomNumber());
            assertEquals(2, result.getFloor());
            assertEquals(3, result.getCapacity());
            verify(roomRepository).save(room);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when room not found")
        void updateThrowsWhenNotFound() {
            // Arrange
            UUID notFoundId = UUID.randomUUID();
            UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder().build();

            when(roomRepository.findByIdAndDormitoryId(notFoundId, dormitory.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> adminDormRoomService.update(admin, notFoundId, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when new room number already exists on another room")
        void updateThrowsWhenDuplicateNumber() {
            // Arrange
            UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder()
                    .roomNumber("202")
                    .build();

            when(roomRepository.findByIdAndDormitoryId(roomId, dormitory.getId())).thenReturn(Optional.of(room));
            when(roomRepository.existsByDormitoryIdAndRoomNumberAndIdNot(dormitory.getId(), "202", roomId)).thenReturn(true);

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminDormRoomService.update(admin, roomId, request));
            assertEquals("A room with this number already exists in the dormitory", ex.getMessage());
            verify(roomRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when updated floor exceeds floors count")
        void updateThrowsWhenFloorExceedsLimit() {
            // Arrange
            UpdateDormRoomRequestDto request = UpdateDormRoomRequestDto.builder()
                    .floor(10)
                    .build();

            when(roomRepository.findByIdAndDormitoryId(roomId, dormitory.getId())).thenReturn(Optional.of(room));

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> adminDormRoomService.update(admin, roomId, request));
        }
    }
}
