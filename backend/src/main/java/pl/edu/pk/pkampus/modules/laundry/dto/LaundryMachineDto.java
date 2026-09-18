package pl.edu.pk.pkampus.modules.laundry.dto;

import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;

import java.util.UUID;

public record LaundryMachineDto(
        UUID id,
        String identifier,
        String floorLocation,
        LaundryMachineStatus status
) {
}
