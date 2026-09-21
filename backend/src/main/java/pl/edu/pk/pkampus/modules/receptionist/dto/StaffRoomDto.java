package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;

import java.time.LocalTime;
import java.util.UUID;

public record StaffRoomDto(
        UUID id,
        String name,
        ThematicRoomStatus status,
        LocalTime openingTime,
        LocalTime closingTime,
        int maxCapacity,
        boolean spansMidnight
) {
}
