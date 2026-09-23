package pl.edu.pk.pkampus.modules.laundry;

import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Encapsulates validation rules and policies for laundry schedule and booking operations.
 */
public final class LaundryBookingValidator {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    public static final int MAX_HORIZON_DAYS = 7;
    public static final int MAX_ACTIVE_IN_ROLLING_DAYS = 2;
    public static final int ROLLING_WINDOW_DAYS = 7;

    private LaundryBookingValidator() {
    }

    public static void validateScheduleRange(LocalDate from, LocalDate to, Clock clock) {
        if (from == null || to == null) {
            throw new BusinessRuleException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("to must be on or after from");
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), WARSAW);
        LocalDate maxDate = today.plusDays(MAX_HORIZON_DAYS);
        if (from.isBefore(today)) {
            throw new BusinessRuleException("from cannot be before today");
        }
        if (to.isAfter(maxDate)) {
            throw new BusinessRuleException("Schedule may only cover up to " + MAX_HORIZON_DAYS + " days ahead");
        }
    }

    public static void validateHorizon(Instant start, Instant now) {
        if (!start.isAfter(now)) {
            throw new BusinessRuleException("Slot start must be in the future");
        }
        Instant maxStart = now.plus(MAX_HORIZON_DAYS, ChronoUnit.DAYS);
        if (start.isAfter(maxStart)) {
            throw new BusinessRuleException("Booking is only allowed up to 7 days ahead");
        }
    }

    public static void validateSlotOnGrid(Dormitory dorm, Instant start, Instant end) {
        OffsetDateTime startWarsaw = start.atZone(WARSAW).toOffsetDateTime();
        LocalTime opening = dorm.getLaundryOpeningTime();
        LocalTime closing = dorm.getLaundryClosingTime();
        int durationMinutes = dorm.getLaundrySlotDurationMinutes();

        LocalTime startTime = startWarsaw.toLocalTime();
        if (startTime.isBefore(opening)) {
            throw new BusinessRuleException("Slot starts before laundry opening hours");
        }
        LocalTime endLocal = end.atZone(WARSAW).toLocalTime();
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
        if (!slotStarts(opening, closing, durationMinutes).contains(startTime)) {
            throw new BusinessRuleException("Slot start is not aligned to the laundry slot grid");
        }
    }

    /**
     * Limits relative to the new reservation date D (Europe/Warsaw):
     * <ul>
     *   <li>at most one active booking on calendar day D</li>
     *   <li>at most {@value #MAX_ACTIVE_IN_ROLLING_DAYS} active bookings with start date in
     *       [{@code D - 6}, {@code D + 6}] (any 7-day span that includes D)</li>
     * </ul>
     */
    public static void validateBookingLimits(
            LaundryBookingRepository laundryBookingRepository,
            Set<LaundryBookingStatus> activeStatuses,
            UUID userId,
            Instant start
    ) {
        LocalDate day = start.atZone(WARSAW).toLocalDate();
        Instant dayStart = day.atStartOfDay(WARSAW).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(WARSAW).toInstant();
        Instant rollingStart = day.minusDays(ROLLING_WINDOW_DAYS - 1L).atStartOfDay(WARSAW).toInstant();
        Instant rollingEnd = day.plusDays(ROLLING_WINDOW_DAYS).atStartOfDay(WARSAW).toInstant();

        long sameDay = laundryBookingRepository.countActiveStartingBetween(
                userId, dayStart, dayEnd, activeStatuses);
        if (sameDay >= 1) {
            throw new BusinessRuleException(
                    "Only one laundry booking per calendar day is allowed"
            );
        }

        long inRollingWindow = laundryBookingRepository.countActiveStartingBetween(
                userId, rollingStart, rollingEnd, activeStatuses);
        if (inRollingWindow >= MAX_ACTIVE_IN_ROLLING_DAYS) {
            throw new BusinessRuleException(
                    "Limit of " + MAX_ACTIVE_IN_ROLLING_DAYS
                            + " active laundry bookings within " + ROLLING_WINDOW_DAYS
                            + " days from the reservation date reached"
            );
        }
    }

    /**
     * Slot starts from opening until a full duration fits before closing.
     * Does not use {@link LocalTime#plusMinutes(long)} in the loop condition — that wraps at midnight
     * (e.g. 22:00 + 180 min → 01:00) and would spin forever for 3h slots with closing 23:00.
     */
    public static List<LocalTime> slotStarts(LocalTime opening, LocalTime closing, int durationMinutes) {
        if (durationMinutes <= 0 || !opening.isBefore(closing)) {
            return List.of();
        }
        List<LocalTime> starts = new ArrayList<>();
        LocalTime slotStart = opening;
        while (true) {
            long minutesUntilClose = ChronoUnit.MINUTES.between(slotStart, closing);
            if (minutesUntilClose < durationMinutes) {
                break;
            }
            starts.add(slotStart);
            LocalTime next = slotStart.plusMinutes(durationMinutes);
            // Safety: LocalTime wraps; stop if advancement did not move forward on the clock
            if (!next.isAfter(slotStart)) {
                break;
            }
            slotStart = next;
        }
        return starts;
    }
}
