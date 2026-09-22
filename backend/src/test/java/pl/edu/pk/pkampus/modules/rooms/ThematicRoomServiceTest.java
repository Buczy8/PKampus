package pl.edu.pk.pkampus.modules.rooms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.rooms.dto.CreateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.ThematicRoomDto;
import pl.edu.pk.pkampus.modules.rooms.dto.UpdateThematicRoomRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ThematicRoomService POJO/Mockito unit tests (AAA)")
class ThematicRoomServiceTest {

    @Mock
    private ThematicRoomRepository thematicRoomRepository;

    @InjectMocks
    private ThematicRoomService thematicRoomService;

    private Dormitory dorm;
    private User admin;
    private User resident;
    private ThematicRoom room;

    @BeforeEach
    void setUp() {
        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Salki")
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();

        room = ThematicRoom.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm)
                .name("Salka Bilardowa")
                .maxCapacity(8)
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(22, 0))
                .spansMidnight(false)
                .maxDurationHours(3)
                .status(ThematicRoomStatus.AVAILABLE)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("listForAdmin tests")
    class ListForAdminTests {

        @Test
        @DisplayName("listForAdmin throws AccessDeniedException when caller is not DORM_ADMIN")
        void listForAdminThrowsWhenNotAdmin() {
            // Arrange & Act & Assert
            assertThrows(AccessDeniedException.class, () -> thematicRoomService.listForAdmin(resident));
        }

        @Test
        @DisplayName("listForAdmin throws BusinessRuleException when admin has no dormitory")
        void listForAdminThrowsWhenNoDorm() {
            // Arrange
            admin.setDormitory(null);

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> thematicRoomService.listForAdmin(admin));
        }

        @Test
        @DisplayName("listForAdmin returns rooms for admin's dormitory")
        void listForAdminSuccess() {
            // Arrange
            when(thematicRoomRepository.findAllByDormitoryIdOrderByNameAsc(dorm.getId()))
                    .thenReturn(List.of(room));

            // Act
            List<ThematicRoomDto> dtos = thematicRoomService.listForAdmin(admin);

            // Assert
            assertEquals(1, dtos.size());
            assertEquals(room.getId(), dtos.get(0).getId());
            assertEquals("Salka Bilardowa", dtos.get(0).getName());
        }
    }

    @Nested
    @DisplayName("create tests")
    class CreateTests {

        @Test
        @DisplayName("create throws BusinessRuleException when hours are invalid")
        void createThrowsOnInvalidHours() {
            // Arrange (closing before opening without spansMidnight)
            CreateThematicRoomRequestDto request = CreateThematicRoomRequestDto.builder()
                    .name("Salka")
                    .maxCapacity(10)
                    .openingTime(LocalTime.of(20, 0))
                    .closingTime(LocalTime.of(10, 0))
                    .spansMidnight(false)
                    .maxDurationHours(2)
                    .build();

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> thematicRoomService.create(admin, request));
            assertTrue(ex.getMessage().contains("Closing time must be after opening time"));
        }

        @Test
        @DisplayName("create succeeds with spansMidnight true and closing before opening")
        void createSuccessSpansMidnight() {
            // Arrange
            CreateThematicRoomRequestDto request = CreateThematicRoomRequestDto.builder()
                    .name("Salka Nocna")
                    .maxCapacity(12)
                    .openingTime(LocalTime.of(22, 0))
                    .closingTime(LocalTime.of(4, 0))
                    .spansMidnight(true)
                    .maxDurationHours(4)
                    .build();

            when(thematicRoomRepository.save(any(ThematicRoom.class))).thenAnswer(inv -> {
                ThematicRoom r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                r.setCreatedAt(Instant.now());
                return r;
            });

            // Act
            ThematicRoomDto dto = thematicRoomService.create(admin, request);

            // Assert
            assertNotNull(dto);
            assertEquals("Salka Nocna", dto.getName());
            assertTrue(dto.isSpansMidnight());
            assertEquals(ThematicRoomStatus.AVAILABLE, dto.getStatus());
        }
    }

    @Nested
    @DisplayName("update tests")
    class UpdateTests {

        @Test
        @DisplayName("update throws ResourceNotFoundException when room not found")
        void updateThrowsWhenNotFound() {
            // Arrange
            UUID roomId = UUID.randomUUID();
            UpdateThematicRoomRequestDto request = UpdateThematicRoomRequestDto.builder()
                    .name("Nowa nazwa")
                    .build();

            when(thematicRoomRepository.findByIdAndDormitoryId(roomId, dorm.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> thematicRoomService.update(admin, roomId, request));
        }

        @Test
        @DisplayName("update successfully updates fields and revalidates hours")
        void updateSuccess() {
            // Arrange
            UpdateThematicRoomRequestDto request = UpdateThematicRoomRequestDto.builder()
                    .name("Nowa Salka Bilardowa")
                    .maxCapacity(10)
                    .status(ThematicRoomStatus.MAINTENANCE)
                    .build();

            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));
            when(thematicRoomRepository.save(room)).thenReturn(room);

            // Act
            ThematicRoomDto result = thematicRoomService.update(admin, room.getId(), request);

            // Assert
            assertNotNull(result);
            assertEquals("Nowa Salka Bilardowa", room.getName());
            assertEquals(10, room.getMaxCapacity());
            assertEquals(ThematicRoomStatus.MAINTENANCE, room.getStatus());
            verify(thematicRoomRepository).save(room);
        }
    }

    @Nested
    @DisplayName("listAvailableForResident tests")
    class ListForResidentTests {

        @Test
        @DisplayName("listAvailableForResident throws AccessDeniedException when caller is not RESIDENT")
        void listForResidentThrowsWhenNotResident() {
            // Arrange & Act & Assert
            assertThrows(AccessDeniedException.class,
                    () -> thematicRoomService.listAvailableForResident(admin));
        }

        @Test
        @DisplayName("listAvailableForResident returns available rooms for resident's dormitory")
        void listForResidentSuccess() {
            // Arrange
            when(thematicRoomRepository.findAllByDormitoryIdAndStatusOrderByNameAsc(dorm.getId(), ThematicRoomStatus.AVAILABLE))
                    .thenReturn(List.of(room));

            // Act
            List<ThematicRoomDto> dtos = thematicRoomService.listAvailableForResident(resident);

            // Assert
            assertEquals(1, dtos.size());
            assertEquals(room.getId(), dtos.get(0).getId());
        }
    }
}
