package pl.edu.pk.pkampus.modules.laundry.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LaundrySlotDto(
        UUID machineId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        LaundrySlotState state,
        UUID bookingId,
        String residentLabel,
        LaundryBookingStatus bookingStatus
) {
    public LaundrySlotDto(
            UUID machineId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            LaundrySlotState state
    ) {
        this(machineId, startTime, endTime, state, null, null, null);
    }
}
