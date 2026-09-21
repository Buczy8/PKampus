package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;

import java.util.UUID;

public record DeskLaundryMachineDto(
        UUID id,
        String machineIdentifier,
        String floorLocation,
        LaundryMachineStatus status,
        String notes
) {
}
