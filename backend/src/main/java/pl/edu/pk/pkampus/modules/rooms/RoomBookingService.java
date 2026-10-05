package pl.edu.pk.pkampus.modules.rooms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.exception.SlotConflictException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.rooms.dto.CreateRoomBookingRequestDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomAvailabilityDto;
import pl.edu.pk.pkampus.modules.rooms.dto.RoomBookingDto;
import pl.edu.pk.pkampus.modules.sanctions.SanctionRepository;
import pl.edu.pk.pkampus.modules.sanctions.SanctionType;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomBookingService {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final int MAX_HORIZON_DAYS = 14;
    private static final int MAX_AVAILABILITY_DAYS = 14;

    private static final Set<RoomBookingStatus> ACTIVE_STATUSES =
            EnumSet.of(RoomBookingStatus.CONFIRMED, RoomBookingStatus.KEY_ISSUED);

    private final RoomBookingRepository roomBookingRepository;
    private final ThematicRoomRepository thematicRoomRepository;
    private final SanctionRepository sanctionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RoomAvailabilityDto availability(User user, UUID roomId, LocalDate from, LocalDate to) {
        Dormitory dorm = requireResidentDormitory(user);
        ThematicRoom room = requireRoomInDorm(roomId, dorm.getId());
        validateAvailabilityRange(from, to);

        Instant rangeStart = from.atStartOfDay(WARSAW).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(WARSAW).toInstant();

        List<RoomAvailabilityDto.BusyIntervalDto> busy = roomBookingRepository
                .findActiveForRoomInRange(room.getId(), rangeStart, rangeEnd, ACTIVE_STATUSES)
                .stream()
                .map(b -> new RoomAvailabilityDto.BusyIntervalDto(
                        b.getStartTime().atZone(WARSAW).toOffsetDateTime(),
                        b.getEndTime().atZone(WARSAW).toOffsetDateTime()
                ))
                .toList();

        return new RoomAvailabilityDto(room.getId(), busy);
    }

    @Transactional(readOnly = true)
    public List<RoomBookingDto> listMyBookings(User user) {
        requireResidentDormitory(user);
        return roomBookingRepository
                .findByUserIdAndStatusInOrderByStartTimeAsc(user.getId(), ACTIVE_STATUSES)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public RoomBookingDto createBooking(User user, CreateRoomBookingRequestDto request) {
        Dormitory dorm = requireResidentDormitory(user);
        rejectIfRoomBanned(user);

        ThematicRoom room = requireRoomInDorm(request.roomId(), dorm.getId());
        if (room.getStatus() != ThematicRoomStatus.AVAILABLE) {
            throw new BusinessRuleException("Thematic room is under maintenance");
        }

        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new BusinessRuleException("Terms must be accepted");
        }

        Instant start = request.startTime().toInstant();
        Instant end = request.endTime().toInstant();
        Instant now = clock.instant();

        if (!end.isAfter(start)) {
            throw new BusinessRuleException("endTime must be after startTime");
        }

        validateHorizon(start, now);
        validateWholeHours(start, end);
        validateDuration(room, start, end);
        validateOpeningWindow(room, start, end);
        validateOneActivePerDay(user, start, end, now);

        if (request.participantsCount() == null || request.participantsCount() < 1) {
            throw new BusinessRuleException("participantsCount must be at least 1");
        }
        if (request.participantsCount() > room.getMaxCapacity()) {
            throw new BusinessRuleException("Number of participants exceeds room capacity");
        }

        String purpose = request.purpose().trim();
        if (purpose.isEmpty()) {
            throw new BusinessRuleException("Purpose is required");
        }

        if (roomBookingRepository.existsOverlapping(room.getId(), start, end, ACTIVE_STATUSES)) {
            throw new SlotConflictException("Room slot overlaps an existing reservation");
        }

        RoomBooking booking = RoomBooking.builder()
                .room(room)
                .user(user)
                .startTime(start)
                .endTime(end)
                .participantsCount(request.participantsCount())
                .purpose(purpose)
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build();

        try {
            booking = roomBookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isBookingWindowViolation(ex)) {
                throw windowViolation(ex);
            }
            throw new SlotConflictException("Room slot overlaps an existing reservation");
        }

        log.info("Resident {} booked thematic room {} [{} – {}]",
                user.getEmail(), room.getName(), start, end);
        return toDto(booking);
    }

    @Transactional
    public RoomBookingDto cancelBooking(User user, UUID bookingId) {
        requireResidentDormitory(user);

        RoomBooking booking = roomBookingRepository
                .findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Room booking not found"));

        if (booking.getStatus() != RoomBookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only CONFIRMED bookings can be cancelled by the resident");
        }
        if (!booking.getStartTime().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Cannot cancel a booking after it has started");
        }

        booking.setStatus(RoomBookingStatus.CANCELLED_USER);
        return toDto(roomBookingRepository.save(booking));
    }

    private Dormitory requireResidentDormitory(User user) {
        if (user.getRole() != UserRole.RESIDENT) {
            throw new AccountStatusException("Only residents can book thematic rooms");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("Account must be ACTIVE to book thematic rooms");
        }
        if (user.getDormitory() == null) {
            throw new AccountStatusException("Resident is not assigned to a dormitory");
        }
        return user.getDormitory();
    }

    private void rejectIfRoomBanned(User user) {
        var bans = sanctionRepository.findActiveByUserAndType(
                user.getId(), SanctionType.ROOM_BAN, LocalDate.ofInstant(clock.instant(), WARSAW));
        if (!bans.isEmpty()) {
            throw new AccountStatusException(
                    "Active ROOM_BAN until " + bans.getFirst().getEndDate()
                            + ". Thematic room reservations are blocked campus-wide.");
        }
    }

    private void validateOneActivePerDay(User user, Instant start, Instant end, Instant now) {
        LocalDate fromDay = start.atZone(WARSAW).toLocalDate();
        LocalDate toDay = end.atZone(WARSAW).toLocalDate();
        // createBooking guarantees end > start, so only the midnight edge needs handling here
        if (end.atZone(WARSAW).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            toDay = toDay.minusDays(1);
        }

        for (LocalDate day = fromDay; !day.isAfter(toDay); day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(WARSAW).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(WARSAW).toInstant();
            if (roomBookingRepository.existsActiveNotEndedForUserOnDay(
                    user.getId(), dayStart, dayEnd, now, ACTIVE_STATUSES)) {
                throw new BusinessRuleException(
                        "Only one active room reservation per day is allowed "
                                + "(the next one is possible after the previous one ends)");
            }
        }
    }

    private ThematicRoom requireRoomInDorm(UUID roomId, UUID dormitoryId) {
        return thematicRoomRepository.findByIdAndDormitoryId(roomId, dormitoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Thematic room not found in your dormitory"));
    }

    private void validateAvailabilityRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("to must be on or after from");
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), WARSAW);
        if (from.isBefore(today.minusDays(1))) {
            throw new BusinessRuleException("from is too far in the past");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_AVAILABILITY_DAYS) {
            throw new BusinessRuleException(
                    "Availability may only cover up to " + MAX_AVAILABILITY_DAYS + " days");
        }
    }

    private void validateHorizon(Instant start, Instant now) {
        if (!start.isAfter(now)) {
            throw new BusinessRuleException("Booking start must be in the future");
        }
        Instant maxStart = now.plus(MAX_HORIZON_DAYS, ChronoUnit.DAYS);
        if (start.isAfter(maxStart)) {
            throw new BusinessRuleException(
                    "Booking is only allowed up to " + MAX_HORIZON_DAYS + " days ahead");
        }
    }

    private void validateDuration(ThematicRoom room, Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        Duration max = Duration.ofHours(room.getMaxDurationHours());
        if (duration.compareTo(max) > 0) {
            throw new BusinessRuleException(
                    "Reservation duration exceeds max " + room.getMaxDurationHours() + " hours for this room");
        }
        if (duration.toMinutes() < 60) {
            throw new BusinessRuleException("Reservation must be at least 1 hour");
        }
    }

    private void validateWholeHours(Instant start, Instant end) {
        ZonedDateTime startZ = start.atZone(WARSAW);
        ZonedDateTime endZ = end.atZone(WARSAW);
        if (isNotWholeHour(startZ) || isNotWholeHour(endZ)) {
            throw new BusinessRuleException("Reservations must start and end on the hour (HH:00)");
        }
    }

    private static boolean isNotWholeHour(ZonedDateTime zdt) {
        return zdt.getMinute() != 0 || zdt.getSecond() != 0 || zdt.getNano() != 0;
    }

    /**
     * Validates that [start, end) fits the room opening window in Europe/Warsaw.
     */
    void validateOpeningWindow(ThematicRoom room, Instant start, Instant end) {
        ZonedDateTime startZ = start.atZone(WARSAW);
        ZonedDateTime endZ = end.atZone(WARSAW);
        LocalTime opening = room.getOpeningTime();
        LocalTime closing = room.getClosingTime();

        if (!room.isSpansMidnight()) {
            if (!startZ.toLocalDate().equals(endZ.toLocalDate())) {
                throw new BusinessRuleException("Reservation must stay within a single calendar day");
            }
            LocalTime startT = startZ.toLocalTime();
            LocalTime endT = endZ.toLocalTime();
            if (startT.isBefore(opening)) {
                throw new BusinessRuleException("Reservation starts before room opening hours");
            }
            if (endT.isAfter(closing)) {
                throw new BusinessRuleException("Reservation ends after room closing hours");
            }
            return;
        }

        // spans midnight: window = [opening on sessionDay, closing on sessionDay+1]
        LocalDate sessionDay;
        if (!startZ.toLocalTime().isBefore(opening)) {
            sessionDay = startZ.toLocalDate();
        } else if (startZ.toLocalTime().isBefore(closing)) {
            sessionDay = startZ.toLocalDate().minusDays(1);
        } else {
            throw new BusinessRuleException("Reservation starts outside room opening hours");
        }

        ZonedDateTime windowStart = sessionDay.atTime(opening).atZone(WARSAW);
        ZonedDateTime windowEnd = sessionDay.plusDays(1).atTime(closing).atZone(WARSAW);

        if (startZ.isBefore(windowStart) || endZ.isAfter(windowEnd)) {
            throw new BusinessRuleException(
                    "Reservation must fit within opening hours ("
                            + opening + "–" + closing + " next day)");
        }
    }

    private RoomBookingDto toDto(RoomBooking booking) {
        return new RoomBookingDto(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getUser().getId(),
                booking.getStartTime().atZone(WARSAW).toOffsetDateTime(),
                booking.getEndTime().atZone(WARSAW).toOffsetDateTime(),
                booking.getParticipantsCount(),
                booking.getPurpose(),
                booking.getStatus(),
                booking.getCreatedAt().atZone(WARSAW).toOffsetDateTime()
        );
    }

    /**
     * Detects last-line DB window-trigger rejections (V12 check_room_booking_window,
     * SQLSTATE P0001 with 'room_booking_window:' prefix) so they surface as 422
     * business-rule errors instead of misleading 409 slot conflicts.
     */
    private static boolean isBookingWindowViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String message = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        return message != null && message.contains("room_booking_window:");
    }

    private static BusinessRuleException windowViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String raw = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        String firstLine = raw != null ? raw.split("\\R")[0].replaceFirst("^(ERROR:\\s*)", "").trim() : "";
        return new BusinessRuleException(firstLine.isEmpty() ? "Booking violates opening-hours window" : firstLine);
    }
}
