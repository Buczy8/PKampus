package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;

import java.util.UUID;

public record DeskThematicRoomDto(
        UUID id,
        String name,
        ThematicRoomStatus status
) {
}
