package pl.edu.pk.pkampus.modules.laundry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLaundryMachineDto {

    private UUID id;
    private UUID dormitoryId;
    private String machineIdentifier;
    private String floorLocation;
    private LaundryMachineStatus status;
    private Instant createdAt;
}
