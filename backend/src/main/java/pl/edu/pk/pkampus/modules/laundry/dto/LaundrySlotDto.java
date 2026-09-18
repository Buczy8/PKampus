package pl.edu.pk.pkampus.modules.laundry.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LaundrySlotDto(
        UUID machineId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        LaundrySlotState state
) {
}
