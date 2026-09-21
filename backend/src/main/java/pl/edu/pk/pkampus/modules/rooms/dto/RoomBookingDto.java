package pl.edu.pk.pkampus.modules.rooms.dto;

import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomBookingDto(
        UUID id,
        UUID roomId,
        String roomName,
        UUID userId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Integer participantsCount,
        String purpose,
        RoomBookingStatus status,
        OffsetDateTime createdAt
) {
}
