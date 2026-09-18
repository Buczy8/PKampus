package pl.edu.pk.pkampus.modules.laundry;

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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
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

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int MAX_HORIZON_DAYS = 7;
    private static final int MAX_ACTIVE_PER_WEEK = 2;
    private static final Set<LaundryBookingStatus> ACTIVE_STATUSES =
            EnumSet.of(LaundryBookingStatus.CONFIRMED, LaundryBookingStatus.KEY_ISSUED);

    private final LaundryMachineRepository laundryMachineRepository;
    private final LaundryBookingRepository laundryBookingRepository;

    @Transactional(readOnly = true)
    public LaundryScheduleResponseDto getSchedule(User user, LocalDate from, LocalDate to) {
        Dormitory dorm = requireResidentDormitory(user);
        validateScheduleRange(from, to);

        List<LaundryMachine> machines =
                laundryMachineRepository.findAllByDormitoryIdOrderByMachineIdentifierAsc(dorm.getId());

        Instant rangeStart = from.atStartOfDay(WARSAW).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(WARSAW).toInstant();

        List<LaundryBooking> bookings = laundryBookingRepository.findActiveInRange(
                dorm.getId(), rangeStart, rangeEnd, ACTIVE_STATUSES);

        Map<String, LaundryBooking> bookingByMachineAndStart = new HashMap<>();
        for (LaundryBooking booking : bookings) {
            String key = bookingKey(booking.getMachine().getId(), booking.getStartTime());
            bookingByMachineAndStart.put(key, booking);
        }

        int durationMinutes = dorm.getLaundrySlotDurationMinutes();
        LocalTime opening = dorm.getLaundryOpeningTime();
        LocalTime closing = dorm.getLaundryClosingTime();
        Instant now = Instant.now();

        List<LaundryScheduleDayDto> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<LaundrySlotDto> slots = new ArrayList<>();
            for (LaundryMachine machine : machines) {
                for (LocalTime slotStart = opening;
                     !slotStart.plusMinutes(durationMinutes).isAfter(closing);
                     slotStart = slotStart.plusMinutes(durationMinutes)) {

                    Instant startInstant = date.atTime(slotStart).atZone(WARSAW).toInstant();
                    Instant endInstant = startInstant.plus(durationMinutes, ChronoUnit.MINUTES);

                    LaundrySlotState state;
                    if (machine.getStatus() == LaundryMachineStatus.OUT_OF_ORDER
                            || !startInstant.isAfter(now)) {
                        state = LaundrySlotState.UNAVAILABLE;
                    } else {
                        LaundryBooking booking = bookingByMachineAndStart.get(
                                bookingKey(machine.getId(), startInstant));
                        if (booking == null) {
                            // Also match by overlap in case of slight timestamp differences
                            booking = findOverlapping(bookings, machine.getId(), startInstant, endInstant);
                        }
                        if (booking == null) {
                            state = LaundrySlotState.FREE;
                        } else if (booking.getUser().getId().equals(user.getId())) {
                            state = LaundrySlotState.MINE;
                        } else {
                            state = LaundrySlotState.OCCUPIED;
                        }
                    }

                    slots.add(new LaundrySlotDto(
                            machine.getId(),
                            toOffset(startInstant),
                            toOffset(endInstant),
                            state
                    ));
                }
            }
            days.add(new LaundryScheduleDayDto(date, slots));
        }

        List<LaundryMachineDto> machineDtos = machines.stream()
                .map(m -> new LaundryMachineDto(
                        m.getId(),
                        m.getMachineIdentifier(),
                        m.getFloorLocation(),
                        m.getStatus()
                ))
                .toList();

        return new LaundryScheduleResponseDto(opening, closing, durationMinutes, machineDtos, days);
    }

    @Transactional(readOnly = true)
    public List<LaundryBookingDto> listMyBookings(User user) {
        requireResidentDormitory(user);
        return laundryBookingRepository
                .findByUserIdAndStatusInOrderByStartTimeAsc(user.getId(), ACTIVE_STATUSES)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public LaundryBookingDto bookSlot(User user, CreateLaundryBookingRequestDto request) {
        Dormitory dorm = requireResidentDormitory(user);

        LaundryMachine machine = laundryMachineRepository
                .findByIdAndDormitoryId(request.machineId(), dorm.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laundry machine not found in your dormitory"));

        if (machine.getStatus() != LaundryMachineStatus.AVAILABLE) {
            throw new BusinessRuleException("Laundry machine is out of order");
        }

        Instant start = request.startTime().toInstant();
        Instant end = request.endTime().toInstant();
        Instant now = Instant.now();

        int durationMinutes = dorm.getLaundrySlotDurationMinutes();
        Instant expectedEnd = start.plus(durationMinutes, ChronoUnit.MINUTES);
        if (!expectedEnd.equals(end)) {
            throw new BusinessRuleException(
                    "endTime must equal startTime plus slot duration (" + durationMinutes + " minutes)"
            );
        }

        validateSlotOnGrid(dorm, start, end);
        validateHorizon(start, now);

        Instant[] weekBounds = weekBoundsContaining(start);
        long activeInWeek = laundryBookingRepository.countActiveInWeek(
                user.getId(), weekBounds[0], weekBounds[1], ACTIVE_STATUSES);
        if (activeInWeek >= MAX_ACTIVE_PER_WEEK) {
            throw new BusinessRuleException(
                    "Weekly limit of " + MAX_ACTIVE_PER_WEEK
                            + " active laundry bookings (CONFIRMED/KEY_ISSUED) reached"
            );
        }

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
            throw new SlotConflictException("Slot was just taken by another resident");
        }

        return toDto(booking);
    }

    @Transactional
    public LaundryBookingDto cancelBooking(User user, UUID bookingId) {
        requireResidentDormitory(user);

        LaundryBooking booking = laundryBookingRepository
                .findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laundry booking not found"));

        if (booking.getStatus() != LaundryBookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only CONFIRMED bookings can be cancelled by the resident");
        }
        if (!booking.getStartTime().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Cannot cancel a booking after the slot has started");
        }

        booking.setStatus(LaundryBookingStatus.CANCELLED_USER);
        return toDto(laundryBookingRepository.save(booking));
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

    private void validateScheduleRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("to must be on or after from");
        }
        LocalDate today = LocalDate.now(WARSAW);
        LocalDate maxDate = today.plusDays(MAX_HORIZON_DAYS);
        if (from.isBefore(today)) {
            throw new BusinessRuleException("from cannot be before today");
        }
        if (to.isAfter(maxDate)) {
            throw new BusinessRuleException("Schedule may only cover up to " + MAX_HORIZON_DAYS + " days ahead");
        }
    }

    private void validateHorizon(Instant start, Instant now) {
        if (!start.isAfter(now)) {
            throw new BusinessRuleException("Slot start must be in the future");
        }
        Instant maxStart = now.plus(MAX_HORIZON_DAYS, ChronoUnit.DAYS);
        if (start.isAfter(maxStart)) {
            throw new BusinessRuleException("Booking is only allowed up to 7 days ahead");
        }
    }

    private void validateSlotOnGrid(Dormitory dorm, Instant start, Instant end) {
        OffsetDateTime startWarsaw = start.atZone(WARSAW).toOffsetDateTime();
        LocalTime opening = dorm.getLaundryOpeningTime();
        LocalTime closing = dorm.getLaundryClosingTime();
        int durationMinutes = dorm.getLaundrySlotDurationMinutes();

        LocalTime startTime = startWarsaw.toLocalTime();
        if (startTime.isBefore(opening)) {
            throw new BusinessRuleException("Slot starts before laundry opening hours");
        }
        LocalTime endLocal = end.atZone(WARSAW).toLocalTime();
        // end may land on midnight next day only if closing is after start same day; for non-midnight dorms:
        if (!startWarsaw.toLocalDate().equals(end.atZone(WARSAW).toLocalDate())) {
            throw new BusinessRuleException("Laundry slots must stay within a single calendar day");
        }
        if (endLocal.isAfter(closing)) {
            throw new BusinessRuleException("Slot ends after laundry closing hours");
        }

        long minutesFromOpening = Duration.between(opening, startTime).toMinutes();
        if (minutesFromOpening < 0 || minutesFromOpening % durationMinutes != 0) {
            throw new BusinessRuleException("Slot start is not aligned to the laundry slot grid");
        }
    }

    /**
     * @return [weekStartInclusive, weekEndExclusive) in UTC Instant for Europe/Warsaw Monday–Sunday week
     */
    static Instant[] weekBoundsContaining(Instant instant) {
        LocalDate date = instant.atZone(WARSAW).toLocalDate();
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextMonday = monday.plusWeeks(1);
        Instant weekStart = monday.atStartOfDay(WARSAW).toInstant();
        Instant weekEnd = nextMonday.atStartOfDay(WARSAW).toInstant();
        return new Instant[]{weekStart, weekEnd};
    }

    private LaundryBooking findOverlapping(
            List<LaundryBooking> bookings,
            UUID machineId,
            Instant start,
            Instant end
    ) {
        for (LaundryBooking booking : bookings) {
            if (!booking.getMachine().getId().equals(machineId)) {
                continue;
            }
            if (booking.getStartTime().isBefore(end) && booking.getEndTime().isAfter(start)) {
                return booking;
            }
        }
        return null;
    }

    private static String bookingKey(UUID machineId, Instant start) {
        return machineId + "|" + start;
    }

    private LaundryBookingDto toDto(LaundryBooking booking) {
        return new LaundryBookingDto(
                booking.getId(),
                booking.getMachine().getId(),
                booking.getMachine().getMachineIdentifier(),
                booking.getUser().getId(),
                toOffset(booking.getStartTime()),
                toOffset(booking.getEndTime()),
                booking.getStatus(),
                toOffset(booking.getCreatedAt())
        );
    }

    private static OffsetDateTime toOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(WARSAW).toOffsetDateTime();
    }
}
