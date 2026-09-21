package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffRoomBookingSlotDto(
        UUID id,
        UUID roomId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        RoomBookingStatus status,
        String residentLabel,
        int participantsCount
) {
}
