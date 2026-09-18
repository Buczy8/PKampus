package pl.edu.pk.pkampus.modules.laundry.dto;

import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;

import java.time.OffsetDateTime;
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
}
