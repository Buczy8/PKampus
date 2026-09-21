package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeskRoomBookingDto(
        UUID id,
        UUID roomId,
        String roomName,
        UUID residentId,
        String residentFirstName,
        String residentLastName,
        String residentRoomNumber,
        String residentPhoneNumber,
        Integer participantsCount,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        RoomBookingStatus status,
        OffsetDateTime keyIssuedAt
) {
}
