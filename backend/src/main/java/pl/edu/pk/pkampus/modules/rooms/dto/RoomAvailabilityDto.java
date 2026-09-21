package pl.edu.pk.pkampus.modules.rooms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoomAvailabilityDto(
        UUID roomId,
        List<BusyIntervalDto> busy
) {
    public record BusyIntervalDto(
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
    }
}
