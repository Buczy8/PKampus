package pl.edu.pk.pkampus.modules.laundry.dto;

import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;

import java.util.UUID;

public record LaundryMachineDto(
        UUID id,
        String identifier,
        String floorLocation,
        LaundryMachineStatus status
) {
    public static LaundryMachineDto from(LaundryMachine machine) {
        return new LaundryMachineDto(
                machine.getId(),
                machine.getMachineIdentifier(),
                machine.getFloorLocation(),
                machine.getStatus()
        );
    }
}
