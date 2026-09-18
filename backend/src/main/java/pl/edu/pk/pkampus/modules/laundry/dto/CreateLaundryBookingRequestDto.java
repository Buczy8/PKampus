package pl.edu.pk.pkampus.modules.laundry.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateLaundryBookingRequestDto(
        @NotNull UUID machineId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime
) {
}
