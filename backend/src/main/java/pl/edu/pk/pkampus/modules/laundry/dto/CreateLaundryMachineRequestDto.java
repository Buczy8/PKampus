package pl.edu.pk.pkampus.modules.laundry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLaundryMachineRequestDto {

    @NotBlank
    @Size(max = 30)
    private String machineIdentifier;

    @NotBlank
    @Size(max = 50)
    private String floorLocation;
}
