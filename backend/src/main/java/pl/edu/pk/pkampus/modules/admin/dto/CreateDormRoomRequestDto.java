package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDormRoomRequestDto {

    @NotBlank
    @Size(max = 10)
    private String roomNumber;

    @NotNull
    @Min(0)
    @Max(50)
    private Integer floor;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer capacity;
}
