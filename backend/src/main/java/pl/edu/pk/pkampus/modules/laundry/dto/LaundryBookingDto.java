package pl.edu.pk.pkampus.modules.laundry.dto;

import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record LaundryBookingDto(
        UUID id,
        UUID machineId,
        String machineIdentifier,
        UUID userId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        LaundryBookingStatus status,
        OffsetDateTime createdAt
) {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    public static LaundryBookingDto from(LaundryBooking booking) {
        return new LaundryBookingDto(
                booking.getId(),
                booking.getMachine().getId(),
                booking.getMachine().getMachineIdentifier(),
                booking.getUser().getId(),
                booking.getStartTime().atZone(WARSAW).toOffsetDateTime(),
                booking.getEndTime().atZone(WARSAW).toOffsetDateTime(),
                booking.getStatus(),
                booking.getCreatedAt() != null ? booking.getCreatedAt().atZone(WARSAW).toOffsetDateTime() : null
        );
    }
}
