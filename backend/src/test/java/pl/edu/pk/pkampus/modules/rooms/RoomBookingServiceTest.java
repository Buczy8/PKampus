package pl.edu.pk.pkampus.modules.rooms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.exception.SlotConflictException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.rooms.dto.CreateRoomBookingRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomAvailabilityDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomBookingDto;
import pl.edu.pk.pkampus.modules.sanctions.Sanction;
import pl.edu.pk.pkampus.modules.sanctions.SanctionRepository;
import pl.edu.pk.pkampus.modules.sanctions.SanctionType;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomBookingService POJO/Mockito unit tests (AAA)")
class RoomBookingServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Mock
    private RoomBookingRepository roomBookingRepository;

    @Mock
    private ThematicRoomRepository thematicRoomRepository;

    @Mock
    private SanctionRepository sanctionRepository;

    @InjectMocks
    private RoomBookingService roomBookingService;

    private Dormitory dorm;
    private User resident;
    private ThematicRoom room;

    @BeforeEach
    void setUp() {
        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Pokoje")
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
                .name("Salka Muzyczna")
                .maxCapacity(5)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build();
    }

    @Nested
    @DisplayName("availability tests")
    class AvailabilityTests {

        @Test
        @DisplayName("availability throws AccountStatusException when user is not ACTIVE")
        void availabilityThrowsWhenNotActive() {
            // Arrange
            resident.setStatus(UserStatus.BLOCKED);
            LocalDate today = LocalDate.now(WARSAW);

            // Act & Assert
            assertThrows(AccountStatusException.class,
                    () -> roomBookingService.availability(resident, room.getId(), today, today));
        }

        @Test
        @DisplayName("availability returns busy intervals")
        void availabilityReturnsBusyIntervals() {
            // Arrange
            LocalDate today = LocalDate.now(WARSAW);
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));
            when(roomBookingRepository.findActiveForRoomInRange(eq(room.getId()), any(), any(), any()))
                    .thenReturn(List.of());

            // Act
            RoomAvailabilityDto result = roomBookingService.availability(resident, room.getId(), today, today.plusDays(1));

            // Assert
            assertNotNull(result);
            assertEquals(room.getId(), result.roomId());
            assertEquals(0, result.busy().size());
        }
    }

    @Nested
    @DisplayName("createBooking tests")
    class CreateBookingTests {

        @Test
        @DisplayName("createBooking throws AccountStatusException when resident has active ROOM_BAN")
        void createBookingThrowsWhenBanned() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 2, "Ćwiczenia na gitarze", true
            );

            Sanction ban = Sanction.builder()
                    .id(UUID.randomUUID())
                    .user(resident)
                    .sanctionType(SanctionType.ROOM_BAN)
                    .endDate(date.plusDays(5))
                    .build();

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of(ban));

            // Act & Assert
            AccountStatusException ex = assertThrows(AccountStatusException.class,
                    () -> roomBookingService.createBooking(resident, request));
            assertTrue(ex.getMessage().contains("Active ROOM_BAN"));
        }

        @Test
        @DisplayName("createBooking throws BusinessRuleException when room is MAINTENANCE")
        void createBookingThrowsWhenRoomMaintenance() {
            // Arrange
            room.setStatus(ThematicRoomStatus.MAINTENANCE);
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 2, "Ćwiczenia", true
            );

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of());
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> roomBookingService.createBooking(resident, request));
            assertTrue(ex.getMessage().contains("under maintenance"));
        }

        @Test
        @DisplayName("createBooking throws BusinessRuleException when not whole hour")
        void createBookingThrowsWhenNotWholeHour() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 15).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 2, "Ćwiczenia", true
            );

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of());
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> roomBookingService.createBooking(resident, request));
            assertTrue(ex.getMessage().contains("must start and end on the hour"));
        }

        @Test
        @DisplayName("createBooking throws BusinessRuleException when duration exceeds max")
        void createBookingThrowsWhenDurationTooLong() {
            // Arrange (room max duration is 4 hours, request is 5)
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(5);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 2, "Ćwiczenia", true
            );

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of());
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> roomBookingService.createBooking(resident, request));
            assertTrue(ex.getMessage().contains("Reservation duration exceeds max"));
        }

        @Test
        @DisplayName("createBooking throws BusinessRuleException when participants exceed capacity")
        void createBookingThrowsWhenCapacityExceeded() {
            // Arrange (capacity is 5, requested is 6)
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 6, "Ćwiczenia", true
            );

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of());
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> roomBookingService.createBooking(resident, request));
            assertTrue(ex.getMessage().contains("Przekroczono limit osób"));
        }

        @Test
        @DisplayName("createBooking throws SlotConflictException when booking overlaps")
        void createBookingThrowsWhenOverlapping() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 2, "Ćwiczenia", true
            );

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of());
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));
            when(roomBookingRepository.existsOverlapping(eq(room.getId()), any(), any(), any()))
                    .thenReturn(true);

            // Act & Assert
            SlotConflictException ex = assertThrows(SlotConflictException.class,
                    () -> roomBookingService.createBooking(resident, request));
            assertTrue(ex.getMessage().contains("overlaps an existing reservation"));
        }

        @Test
        @DisplayName("createBooking succeeds for valid future reservation")
        void createBookingSuccess() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(2);
            OffsetDateTime start = date.atTime(12, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2);
            CreateRoomBookingRequestDto request = new CreateRoomBookingRequestDto(
                    room.getId(), start, end, 3, "Próba zespołu", true
            );

            UUID bookingId = UUID.randomUUID();
            RoomBooking saved = RoomBooking.builder()
                    .id(bookingId)
                    .room(room)
                    .user(resident)
                    .startTime(start.toInstant())
                    .endTime(end.toInstant())
                    .participantsCount(3)
                    .purpose("Próba zespołu")
                    .status(RoomBookingStatus.CONFIRMED)
                    .createdAt(Instant.now())
                    .build();

            when(sanctionRepository.findActiveByUserAndType(eq(resident.getId()), eq(SanctionType.ROOM_BAN), any()))
                    .thenReturn(List.of());
            when(thematicRoomRepository.findByIdAndDormitoryId(room.getId(), dorm.getId()))
                    .thenReturn(Optional.of(room));
            when(roomBookingRepository.existsActiveNotEndedForUserOnDay(eq(resident.getId()), any(), any(), any(), any()))
                    .thenReturn(false);
            when(roomBookingRepository.existsOverlapping(eq(room.getId()), any(), any(), any()))
                    .thenReturn(false);
            when(roomBookingRepository.saveAndFlush(any())).thenReturn(saved);

            // Act
            RoomBookingDto dto = roomBookingService.createBooking(resident, request);

            // Assert
            assertNotNull(dto);
            assertEquals(bookingId, dto.id());
            assertEquals(RoomBookingStatus.CONFIRMED, dto.status());
            assertEquals(3, dto.participantsCount());
            assertEquals("Próba zespołu", dto.purpose());
        }
    }

    @Nested
    @DisplayName("cancelBooking tests")
    class CancelBookingTests {

        @Test
        @DisplayName("cancelBooking throws ResourceNotFoundException when booking does not exist")
        void cancelBookingThrowsWhenNotFound() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            when(roomBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> roomBookingService.cancelBooking(resident, bookingId));
        }

        @Test
        @DisplayName("cancelBooking throws IllegalArgumentException when booking has already started")
        void cancelBookingThrowsWhenAlreadyStarted() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            RoomBooking booking = RoomBooking.builder()
                    .id(bookingId)
                    .room(room)
                    .user(resident)
                    .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                    .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .status(RoomBookingStatus.CONFIRMED)
                    .build();

            when(roomBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.of(booking));

            // Act & Assert
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> roomBookingService.cancelBooking(resident, bookingId));
            assertTrue(ex.getMessage().contains("Cannot cancel a booking after it has started"));
        }

        @Test
        @DisplayName("cancelBooking cancels future confirmed booking")
        void cancelBookingSuccess() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            RoomBooking booking = RoomBooking.builder()
                    .id(bookingId)
                    .room(room)
                    .user(resident)
                    .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(4, ChronoUnit.HOURS))
                    .status(RoomBookingStatus.CONFIRMED)
                    .createdAt(Instant.now())
                    .build();

            when(roomBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.of(booking));
            when(roomBookingRepository.save(booking)).thenReturn(booking);

            // Act
            RoomBookingDto dto = roomBookingService.cancelBooking(resident, bookingId);

            // Assert
            assertNotNull(dto);
            assertEquals(RoomBookingStatus.CANCELLED_USER, booking.getStatus());
            verify(roomBookingRepository).save(booking);
        }
    }
}
