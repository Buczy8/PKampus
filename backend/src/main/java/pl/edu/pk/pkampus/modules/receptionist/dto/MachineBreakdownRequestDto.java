package pl.edu.pk.pkampus.modules.receptionist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MachineBreakdownRequestDto(
        @NotBlank @Size(max = 2000) String reason
) {
}
