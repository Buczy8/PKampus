package pl.edu.pk.pkampus.modules.laundry;

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
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryBookingRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryBookingDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleResponseDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundrySlotDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundrySlotState;
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
@DisplayName("LaundryService POJO/Mockito unit tests (AAA)")
class LaundryServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Mock
    private LaundryMachineRepository laundryMachineRepository;

    @Mock
    private LaundryBookingRepository laundryBookingRepository;

    @InjectMocks
    private LaundryService laundryService;

    private Dormitory dorm;
    private User resident;
    private User otherResident;
    private LaundryMachine machine1;
    private LaundryMachine machine2;

    @BeforeEach
    void setUp() {
        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Pralnia")
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .firstName("Jan")
                .lastName("Kowalski")
                .declaredRoomNumber("205")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();

        otherResident = User.builder()
                .id(UUID.randomUUID())
                .email("inny@pk.edu.pl")
                .firstName("Anna")
                .lastName("Nowak")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build();

        machine1 = LaundryMachine.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm)
                .machineIdentifier("Pralka 1")
                .floorLocation("Pralnia p. 0")
                .status(LaundryMachineStatus.AVAILABLE)
                .build();

        machine2 = LaundryMachine.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm)
                .machineIdentifier("Pralka 2")
                .floorLocation("Pralnia p. 0")
                .status(LaundryMachineStatus.OUT_OF_ORDER)
                .build();
    }

    @Nested
    @DisplayName("getSchedule tests")
    class GetScheduleTests {

        @Test
        @DisplayName("Should throw AccountStatusException when resident status is not ACTIVE")
        void getScheduleThrowsWhenNotActive() {
            // Arrange
            resident.setStatus(UserStatus.BLOCKED);
            LocalDate today = LocalDate.now(WARSAW);

            // Act & Assert
            assertThrows(AccountStatusException.class, () -> laundryService.getSchedule(resident, today, today));
        }

        @Test
        @DisplayName("Should throw AccountStatusException when resident has no dormitory")
        void getScheduleThrowsWhenNoDormitory() {
            // Arrange
            resident.setDormitory(null);
            LocalDate today = LocalDate.now(WARSAW);

            // Act & Assert
            assertThrows(AccountStatusException.class, () -> laundryService.getSchedule(resident, today, today));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException on invalid date range")
        void getScheduleThrowsOnInvalidDateRange() {
            // Arrange
            LocalDate today = LocalDate.now(WARSAW);

            // Act & Assert
            assertThrows(BusinessRuleException.class,
                    () -> laundryService.getSchedule(resident, null, today));
            assertThrows(BusinessRuleException.class,
                    () -> laundryService.getSchedule(resident, today.plusDays(2), today.plusDays(1)));
            assertThrows(BusinessRuleException.class,
                    () -> laundryService.getSchedule(resident, today.minusDays(1), today));
            assertThrows(BusinessRuleException.class,
                    () -> laundryService.getSchedule(resident, today, today.plusDays(8)));
        }

        @Test
        @DisplayName("Should return schedule with FREE, MINE, OCCUPIED and UNAVAILABLE slots")
        void getScheduleReturnsSlots() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            Instant slotStart = date.atTime(10, 0).atZone(WARSAW).toInstant();
            Instant slotEnd = slotStart.plus(180, ChronoUnit.MINUTES);

            Instant slotStart2 = date.atTime(13, 0).atZone(WARSAW).toInstant();
            Instant slotEnd2 = slotStart2.plus(180, ChronoUnit.MINUTES);

            LaundryBooking myBooking = LaundryBooking.builder()
                    .id(UUID.randomUUID())
                    .machine(machine1)
                    .user(resident)
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            LaundryBooking otherBooking = LaundryBooking.builder()
                    .id(UUID.randomUUID())
                    .machine(machine1)
                    .user(otherResident)
                    .startTime(slotStart2)
                    .endTime(slotEnd2)
                    .status(LaundryBookingStatus.KEY_ISSUED)
                    .build();

            when(laundryMachineRepository.findAllByDormitoryIdOrderByMachineIdentifierAsc(dorm.getId()))
                    .thenReturn(List.of(machine1, machine2));
            when(laundryBookingRepository.findActiveInRange(eq(dorm.getId()), any(), any(), any()))
                    .thenReturn(List.of(myBooking, otherBooking));

            // Act
            LaundryScheduleResponseDto schedule = laundryService.getSchedule(resident, date, date);

            // Assert
            assertNotNull(schedule);
            assertEquals(1, schedule.days().size());
            assertEquals(date, schedule.days().get(0).date());

            List<LaundrySlotDto> slots = schedule.days().get(0).slots();
            // Machine 1 slots: 07:00 (FREE), 10:00 (MINE), 13:00 (OCCUPIED), 16:00 (FREE), 19:00 (FREE)
            // Machine 2 slots: all UNAVAILABLE (since OUT_OF_ORDER)
            assertTrue(slots.stream().anyMatch(s -> s.machineId().equals(machine1.getId()) && s.state() == LaundrySlotState.MINE));
            assertTrue(slots.stream().anyMatch(s -> s.machineId().equals(machine1.getId()) && s.state() == LaundrySlotState.OCCUPIED));
            assertTrue(slots.stream().anyMatch(s -> s.machineId().equals(machine1.getId()) && s.state() == LaundrySlotState.FREE));
            assertTrue(slots.stream().filter(s -> s.machineId().equals(machine2.getId()))
                    .allMatch(s -> s.state() == LaundrySlotState.UNAVAILABLE));
        }
    }

    @Nested
    @DisplayName("getStaffSchedule tests")
    class GetStaffScheduleTests {

        @Test
        @DisplayName("Should return staff schedule with resident label and booking status")
        void getStaffScheduleSuccess() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(2);
            Instant slotStart = date.atTime(7, 0).atZone(WARSAW).toInstant();
            Instant slotEnd = slotStart.plus(180, ChronoUnit.MINUTES);

            LaundryBooking booking = LaundryBooking.builder()
                    .id(UUID.randomUUID())
                    .machine(machine1)
                    .user(resident)
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryMachineRepository.findAllByDormitoryIdOrderByMachineIdentifierAsc(dorm.getId()))
                    .thenReturn(List.of(machine1));
            when(laundryBookingRepository.findActiveInRange(eq(dorm.getId()), any(), any(), any()))
                    .thenReturn(List.of(booking));

            // Act
            LaundryScheduleResponseDto response = laundryService.getStaffSchedule(dorm, date, date);

            // Assert
            assertNotNull(response);
            List<LaundrySlotDto> slots = response.days().get(0).slots();
            LaundrySlotDto occupiedSlot = slots.stream()
                    .filter(s -> s.state() == LaundrySlotState.OCCUPIED)
                    .findFirst()
                    .orElseThrow();

            assertEquals(booking.getId(), occupiedSlot.bookingId());
            assertEquals("Jan Kowalski • pok. 205", occupiedSlot.residentLabel());
            assertEquals(LaundryBookingStatus.CONFIRMED, occupiedSlot.bookingStatus());
        }
    }

    @Nested
    @DisplayName("listMyBookings tests")
    class ListMyBookingsTests {

        @Test
        @DisplayName("Should return active bookings mapped to DTO")
        void listMyBookingsSuccess() {
            // Arrange
            Instant now = Instant.now();
            LaundryBooking b = LaundryBooking.builder()
                    .id(UUID.randomUUID())
                    .machine(machine1)
                    .user(resident)
                    .startTime(now.plus(1, ChronoUnit.HOURS))
                    .endTime(now.plus(4, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .createdAt(now)
                    .build();

            when(laundryBookingRepository.findByUserIdAndStatusInOrderByStartTimeAsc(eq(resident.getId()), any()))
                    .thenReturn(List.of(b));

            // Act
            List<LaundryBookingDto> result = laundryService.listMyBookings(resident);

            // Assert
            assertEquals(1, result.size());
            assertEquals(b.getId(), result.get(0).id());
            assertEquals(machine1.getId(), result.get(0).machineId());
            assertEquals(machine1.getMachineIdentifier(), result.get(0).machineIdentifier());
        }
    }

    @Nested
    @DisplayName("bookSlot tests")
    class BookSlotTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when machine is not found")
        void bookSlotThrowsWhenMachineNotFound() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("Laundry machine not found"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when machine is OUT_OF_ORDER")
        void bookSlotThrowsWhenMachineOutOfOrder() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine2.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine2.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine2));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("out of order"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when end time does not match slot duration")
        void bookSlotThrowsWhenDurationMismatch() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(2); // Should be 3 hours!
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("endTime must equal startTime plus slot duration"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when slot is not aligned to grid")
        void bookSlotThrowsWhenNotAlignedToGrid() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(11, 0).atZone(WARSAW).toOffsetDateTime(); // starts at 11:00 instead of 10:00 or 13:00
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("Slot start is not aligned"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when user already has a booking on that calendar day")
        void bookSlotThrowsWhenAlreadyBookedSameDay() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));
            when(laundryBookingRepository.countActiveStartingBetween(eq(resident.getId()), any(), any(), any()))
                    .thenReturn(1L);

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("Only one laundry booking per calendar day is allowed"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when user reached 2 active bookings in rolling 7-day window")
        void bookSlotThrowsWhenRollingLimitExceeded() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));
            // 0 on same day, but 2 in rolling window
            when(laundryBookingRepository.countActiveStartingBetween(eq(resident.getId()), any(), any(), any()))
                    .thenReturn(0L)
                    .thenReturn(2L);

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("Limit of 2 active laundry bookings"));
        }

        @Test
        @DisplayName("Should throw SlotConflictException when slot overlaps with existing booking")
        void bookSlotThrowsWhenSlotOverlaps() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));
            when(laundryBookingRepository.countActiveStartingBetween(eq(resident.getId()), any(), any(), any()))
                    .thenReturn(0L);
            when(laundryBookingRepository.existsOverlapping(eq(machine1.getId()), any(), any(), any()))
                    .thenReturn(true);

            // Act & Assert
            SlotConflictException ex = assertThrows(SlotConflictException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("Slot was just taken by another resident"));
        }

        @Test
        @DisplayName("Should throw SlotConflictException when database constraint fails on save")
        void bookSlotThrowsWhenDataIntegrityViolation() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(1);
            OffsetDateTime start = date.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));
            when(laundryBookingRepository.countActiveStartingBetween(eq(resident.getId()), any(), any(), any()))
                    .thenReturn(0L);
            when(laundryBookingRepository.existsOverlapping(eq(machine1.getId()), any(), any(), any()))
                    .thenReturn(false);
            when(laundryBookingRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Conflict"));

            // Act & Assert
            SlotConflictException ex = assertThrows(SlotConflictException.class,
                    () -> laundryService.bookSlot(resident, request));
            assertTrue(ex.getMessage().contains("Slot was just taken by another resident"));
        }

        @Test
        @DisplayName("Should successfully book a valid laundry slot")
        void bookSlotSuccess() {
            // Arrange
            LocalDate date = LocalDate.now(WARSAW).plusDays(2);
            OffsetDateTime start = date.atTime(13, 0).atZone(WARSAW).toOffsetDateTime();
            OffsetDateTime end = start.plusHours(3);
            CreateLaundryBookingRequestDto request = new CreateLaundryBookingRequestDto(
                    machine1.getId(), start, end
            );

            UUID bookingId = UUID.randomUUID();
            LaundryBooking saved = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine1)
                    .user(resident)
                    .startTime(start.toInstant())
                    .endTime(end.toInstant())
                    .status(LaundryBookingStatus.CONFIRMED)
                    .createdAt(Instant.now())
                    .build();

            when(laundryMachineRepository.findByIdAndDormitoryId(machine1.getId(), dorm.getId()))
                    .thenReturn(Optional.of(machine1));
            when(laundryBookingRepository.countActiveStartingBetween(eq(resident.getId()), any(), any(), any()))
                    .thenReturn(0L);
            when(laundryBookingRepository.existsOverlapping(eq(machine1.getId()), any(), any(), any()))
                    .thenReturn(false);
            when(laundryBookingRepository.saveAndFlush(any())).thenReturn(saved);

            // Act
            LaundryBookingDto result = laundryService.bookSlot(resident, request);

            // Assert
            assertNotNull(result);
            assertEquals(bookingId, result.id());
            assertEquals(LaundryBookingStatus.CONFIRMED, result.status());
            assertEquals(machine1.getId(), result.machineId());
        }
    }

    @Nested
    @DisplayName("cancelBooking tests")
    class CancelBookingTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when booking does not exist")
        void cancelBookingThrowsWhenNotFound() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            when(laundryBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> laundryService.cancelBooking(resident, bookingId));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when booking is not CONFIRMED")
        void cancelBookingThrowsWhenNotConfirmed() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine1)
                    .user(resident)
                    .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(5, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.KEY_ISSUED)
                    .build();

            when(laundryBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.of(booking));

            // Act & Assert
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> laundryService.cancelBooking(resident, bookingId));
            assertTrue(ex.getMessage().contains("Only CONFIRMED bookings can be cancelled"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when booking slot has already started")
        void cancelBookingThrowsWhenAlreadyStarted() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine1)
                    .user(resident)
                    .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                    .endTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.of(booking));

            // Act & Assert
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> laundryService.cancelBooking(resident, bookingId));
            assertTrue(ex.getMessage().contains("Cannot cancel a booking after the slot has started"));
        }

        @Test
        @DisplayName("Should successfully cancel confirmed future booking")
        void cancelBookingSuccess() {
            // Arrange
            UUID bookingId = UUID.randomUUID();
            LaundryBooking booking = LaundryBooking.builder()
                    .id(bookingId)
                    .machine(machine1)
                    .user(resident)
                    .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
                    .endTime(Instant.now().plus(5, ChronoUnit.HOURS))
                    .status(LaundryBookingStatus.CONFIRMED)
                    .build();

            when(laundryBookingRepository.findByIdAndUserId(bookingId, resident.getId()))
                    .thenReturn(Optional.of(booking));
            when(laundryBookingRepository.save(booking)).thenReturn(booking);

            // Act
            LaundryBookingDto result = laundryService.cancelBooking(resident, bookingId);

            // Assert
            assertNotNull(result);
            assertEquals(LaundryBookingStatus.CANCELLED_USER, booking.getStatus());
            verify(laundryBookingRepository).save(booking);
        }
    }
}
