package pl.edu.pk.pkampus.modules.receptionist.dto;

import java.util.UUID;

public record RoomMaintenanceResponseDto(
        UUID roomId,
        int cancelledCount,
        UUID issueId
) {
}
