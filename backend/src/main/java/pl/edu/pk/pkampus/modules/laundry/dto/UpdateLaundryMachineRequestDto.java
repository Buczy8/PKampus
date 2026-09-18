package pl.edu.pk.pkampus.modules.laundry.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLaundryMachineRequestDto {

    @Size(max = 30)
    private String machineIdentifier;

    @Size(max = 50)
    private String floorLocation;

    private LaundryMachineStatus status;
}
