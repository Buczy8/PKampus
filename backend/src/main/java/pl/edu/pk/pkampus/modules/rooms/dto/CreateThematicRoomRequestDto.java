package pl.edu.pk.pkampus.modules.rooms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateThematicRoomRequestDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Min(1)
    @Max(200)
    private Integer maxCapacity;

    @NotNull
    private LocalTime openingTime;

    @NotNull
    private LocalTime closingTime;

    private Boolean spansMidnight;

    @NotNull
    @Min(1)
    @Max(24)
    private Integer maxDurationHours;

    private String description;

    private ThematicRoomStatus status;
}
