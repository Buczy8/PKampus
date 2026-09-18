package pl.edu.pk.pkampus.modules.rooms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThematicRoomDto {

    private UUID id;
    private UUID dormitoryId;
    private String name;
    private Integer maxCapacity;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private boolean spansMidnight;
    private Integer maxDurationHours;
    private String description;
    private ThematicRoomStatus status;
    private Instant createdAt;
}
