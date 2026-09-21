package pl.edu.pk.pkampus.modules.receptionist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomMaintenanceRequestDto(
        @NotBlank @Size(max = 2000) String reason
) {
}
