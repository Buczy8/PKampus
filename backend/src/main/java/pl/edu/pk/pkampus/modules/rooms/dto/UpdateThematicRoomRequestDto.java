package pl.edu.pk.pkampus.modules.rooms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateThematicRoomRequestDto {

    @Size(max = 100)
    private String name;

    @Min(1)
    @Max(200)
    private Integer maxCapacity;

    private LocalTime openingTime;

    private LocalTime closingTime;

    private Boolean spansMidnight;

    @Min(1)
    @Max(24)
    private Integer maxDurationHours;

    private String description;

    private ThematicRoomStatus status;
}
