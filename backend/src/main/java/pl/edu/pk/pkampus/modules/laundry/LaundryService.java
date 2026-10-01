package pl.edu.pk.pkampus.modules.laundry;

import static pl.edu.pk.pkampus.modules.laundry.LaundryBookingValidator.WARSAW;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.exception.SlotConflictException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryBookingRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryBookingDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryMachineDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleDayDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundryScheduleResponseDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundrySlotDto;
import pl.edu.pk.pkampus.modules.laundry.dto.LaundrySlotState;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LaundryService {

    private static final Set<LaundryBookingStatus> ACTIVE_STATUSES =
            EnumSet.of(LaundryBookingStatus.CONFIRMED, LaundryBookingStatus.KEY_ISSUED);

    private final LaundryMachineRepository laundryMachineRepository;
    private final LaundryBookingRepository laundryBookingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LaundryScheduleResponseDto getSchedule(User user, LocalDate from, LocalDate to) {
        Dormitory dorm = requireResidentDormitory(user);
        return buildScheduleResponse(dorm, from, to, (machine, start, end, booking, now) ->
                resolveResidentSlot(user.getId(), machine, start, end, booking, now));
    }

    /**
     * Staff schedule for a dormitory: OCCUPIED slots carry booking id / resident label;
     * past free slots are UNAVAILABLE; active bookings remain visible even after start.
     */
    @Transactional(readOnly = true)
    public LaundryScheduleResponseDto getStaffSchedule(Dormitory dorm, LocalDate from, LocalDate to) {
        return buildScheduleResponse(dorm, from, to, this::resolveStaffSlot);
    }

    private LaundryScheduleResponseDto buildScheduleResponse(
            Dormitory dorm,
            LocalDate from,
            LocalDate to,
            SlotFactory slotFactory
    ) {
        LaundryBookingValidator.validateScheduleRange(from, to, clock);

        List<LaundryMachine> machines =
                laundryMachineRepository.findAllByDormitoryIdOrderByMachineIdentifierAsc(dorm.getId());

        Instant rangeStart = from.atStartOfDay(WARSAW).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(WARSAW).toInstant();

        List<LaundryBooking> bookings = laundryBookingRepository.findActiveInRange(
                dorm.getId(), rangeStart, rangeEnd, ACTIVE_STATUSES);

        Map<UUID, Map<Instant, LaundryBooking>> exactByMachine = new HashMap<>();
        Map<UUID, List<LaundryBooking>> bookingsByMachine = new HashMap<>();
        for (LaundryBooking booking : bookings) {
            UUID machineId = booking.getMachine().getId();
            exactByMachine.computeIfAbsent(machineId, id -> new HashMap<>())
                    .put(booking.getStartTime(), booking);
            bookingsByMachine.computeIfAbsent(machineId, id -> new ArrayList<>()).add(booking);
        }

        int durationMinutes = dorm.getLaundrySlotDurationMinutes();
        LocalTime opening = dorm.getLaundryOpeningTime();
        LocalTime closing = dorm.getLaundryClosingTime();
        Instant now = clock.instant();

        List<LocalTime> starts = LaundryBookingValidator.slotStarts(opening, closing, durationMinutes);

        List<LaundryScheduleDayDto> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<LaundrySlotDto> slots = new ArrayList<>();
            for (LaundryMachine machine : machines) {
                Map<Instant, LaundryBooking> exact = exactByMachine.get(machine.getId());
                List<LaundryBooking> machineBookings = bookingsByMachine.get(machine.getId());
                for (LocalTime slotStart : starts) {
                    Instant startInstant = date.atTime(slotStart).atZone(WARSAW).toInstant();
                    Instant endInstant = startInstant.plus(durationMinutes, ChronoUnit.MINUTES);

                    LaundryBooking booking = findBooking(exact, machineBookings, startInstant, endInstant);

                    slots.add(slotFactory.createSlot(machine, startInstant, endInstant, booking, now));
                }
            }
            days.add(new LaundryScheduleDayDto(date, slots));
        }

        List<LaundryMachineDto> machineDtos = machines.stream()
                .map(LaundryMachineDto::from)
                .toList();

        return new LaundryScheduleResponseDto(opening, closing, durationMinutes, machineDtos, days);
    }

    private LaundrySlotDto resolveResidentSlot(
            UUID residentId,
            LaundryMachine machine,
            Instant startInstant,
            Instant endInstant,
            LaundryBooking booking,
            Instant now
    ) {
        if (machine.getStatus() == LaundryMachineStatus.OUT_OF_ORDER || !startInstant.isAfter(now)) {
            return slot(machine, startInstant, endInstant, LaundrySlotState.UNAVAILABLE);
        }
        if (booking == null) {
            return slot(machine, startInstant, endInstant, LaundrySlotState.FREE);
        }
        if (booking.getUser().getId().equals(residentId)) {
            return slot(machine, startInstant, endInstant, LaundrySlotState.MINE);
        }
        return slot(machine, startInstant, endInstant, LaundrySlotState.OCCUPIED);
    }

    private LaundrySlotDto resolveStaffSlot(
            LaundryMachine machine,
            Instant startInstant,
            Instant endInstant,
            LaundryBooking booking,
            Instant now
    ) {
        if (machine.getStatus() == LaundryMachineStatus.OUT_OF_ORDER) {
            return slot(machine, startInstant, endInstant, LaundrySlotState.UNAVAILABLE);
        }

        if (booking != null) {
            return new LaundrySlotDto(
                    machine.getId(),
                    toOffset(startInstant),
                    toOffset(endInstant),
                    LaundrySlotState.OCCUPIED,
                    booking.getId(),
                    residentLabel(booking.getUser()),
                    booking.getStatus()
            );
        }

        if (!startInstant.isAfter(now)) {
            return slot(machine, startInstant, endInstant, LaundrySlotState.UNAVAILABLE);
        }

        return slot(machine, startInstant, endInstant, LaundrySlotState.FREE);
    }

    private static LaundrySlotDto slot(
            LaundryMachine machine,
            Instant startInstant,
            Instant endInstant,
            LaundrySlotState state
    ) {
        return new LaundrySlotDto(
                machine.getId(),
                toOffset(startInstant),
                toOffset(endInstant),
                state
        );
    }

    private static String residentLabel(User resident) {
        String label = resident.getFirstName() + " " + resident.getLastName();
        if (resident.getDeclaredRoomNumber() != null && !resident.getDeclaredRoomNumber().isBlank()) {
            label += " • pok. " + resident.getDeclaredRoomNumber();
        }
        return label;
    }

    @FunctionalInterface
    private interface SlotFactory {
        LaundrySlotDto createSlot(
                LaundryMachine machine,
                Instant startInstant,
                Instant endInstant,
                LaundryBooking booking,
                Instant now
        );
    }

    @Transactional(readOnly = true)
    public List<LaundryBookingDto> listMyBookings(User user) {
        requireResidentDormitory(user);
        return laundryBookingRepository
                .findByUserIdAndStatusInOrderByStartTimeAsc(user.getId(), ACTIVE_STATUSES)
                .stream()
                .map(LaundryBookingDto::from)
                .toList();
    }

    @Transactional
    public LaundryBookingDto bookSlot(User user, CreateLaundryBookingRequestDto request) {
        Dormitory dorm = requireResidentDormitory(user);
        LaundryMachine machine = requireAvailableMachine(dorm, request.machineId());

        Instant start = request.startTime().toInstant();
        Instant end = request.endTime().toInstant();
        Instant now = clock.instant();

        validateSlotDuration(dorm, start, end);

        LaundryBookingValidator.validateSlotOnGrid(dorm, start, end);
        LaundryBookingValidator.validateHorizon(start, now);
        LaundryBookingValidator.validateBookingLimits(laundryBookingRepository, ACTIVE_STATUSES, user.getId(), start);

        if (laundryBookingRepository.existsOverlapping(machine.getId(), start, end, ACTIVE_STATUSES)) {
            throw new SlotConflictException("Slot was just taken by another resident");
        }

        LaundryBooking booking = LaundryBooking.builder()
                .machine(machine)
                .user(user)
                .startTime(start)
                .endTime(end)
                .status(LaundryBookingStatus.CONFIRMED)
                .build();

        try {
            booking = laundryBookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isBookingWindowViolation(ex)) {
                throw windowViolation(ex);
            }
            throw new SlotConflictException("Slot was just taken by another resident");
        }

        return LaundryBookingDto.from(booking);
    }

    @Transactional
    public LaundryBookingDto cancelBooking(User user, UUID bookingId) {
        requireResidentDormitory(user);

        LaundryBooking booking = laundryBookingRepository
                .findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laundry booking not found"));

        booking.cancelByUser(clock.instant());
        return LaundryBookingDto.from(laundryBookingRepository.save(booking));
    }

    private LaundryMachine requireAvailableMachine(Dormitory dorm, UUID machineId) {
        LaundryMachine machine = laundryMachineRepository
                .findByIdAndDormitoryId(machineId, dorm.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laundry machine not found in your dormitory"));

        if (machine.getStatus() != LaundryMachineStatus.AVAILABLE) {
            throw new BusinessRuleException("Laundry machine is out of order");
        }
        return machine;
    }

    private static void validateSlotDuration(Dormitory dorm, Instant start, Instant end) {
        int durationMinutes = dorm.getLaundrySlotDurationMinutes();
        if (!start.plus(durationMinutes, ChronoUnit.MINUTES).equals(end)) {
            throw new BusinessRuleException(
                    "endTime must equal startTime plus slot duration (" + durationMinutes + " minutes)"
            );
        }
    }

    /**
     * Detects last-line DB window-trigger rejections (V12 check_laundry_booking_window,
     * SQLSTATE P0001 with 'laundry_booking_window:' prefix) so they surface as 422
     * business-rule errors instead of misleading 409 slot conflicts.
     */
    private static boolean isBookingWindowViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String message = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        return message != null && message.contains("laundry_booking_window:");
    }

    private static BusinessRuleException windowViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String raw = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        String firstLine = raw != null ? raw.split("\\R")[0].replaceFirst("^(ERROR:\\s*)", "").trim() : "";
        return new BusinessRuleException(firstLine.isEmpty() ? "Booking violates opening-hours window" : firstLine);
    }

    private Dormitory requireResidentDormitory(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("Account must be ACTIVE to use laundry reservations");
        }
        if (user.getDormitory() == null) {
            throw new AccountStatusException("Resident is not assigned to a dormitory");
        }
        return user.getDormitory();
    }

    private static LaundryBooking findBooking(
            Map<Instant, LaundryBooking> exact,
            List<LaundryBooking> machineBookings,
            Instant start,
            Instant end
    ) {
        if (exact != null) {
            LaundryBooking hit = exact.get(start);
            if (hit != null) {
                return hit;
            }
        }
        if (machineBookings == null) {
            return null;
        }
        for (LaundryBooking booking : machineBookings) {
            if (booking.getStartTime().isBefore(end) && booking.getEndTime().isAfter(start)) {
                return booking;
            }
        }
        return null;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atZone(WARSAW).toOffsetDateTime();
    }
}
