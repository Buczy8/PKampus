package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomBanRequestDto {

    @NotNull
    @Min(1)
    @Max(3)
    private Integer durationMonths;

    @NotBlank
    private String reason;
}
