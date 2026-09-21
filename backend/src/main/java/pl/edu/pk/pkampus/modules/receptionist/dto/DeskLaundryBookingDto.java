package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeskLaundryBookingDto(
        UUID id,
        UUID machineId,
        String machineIdentifier,
        UUID residentId,
        String residentFirstName,
        String residentLastName,
        String residentRoomNumber,
        String residentPhoneNumber,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        LaundryBookingStatus status,
        OffsetDateTime keyIssuedAt
) {
}
