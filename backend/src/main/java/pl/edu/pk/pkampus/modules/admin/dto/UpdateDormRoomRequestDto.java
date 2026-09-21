package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDormRoomRequestDto {

    @Size(max = 10)
    private String roomNumber;

    @Min(0)
    @Max(50)
    private Integer floor;

    @Min(1)
    @Max(10)
    private Integer capacity;
}
