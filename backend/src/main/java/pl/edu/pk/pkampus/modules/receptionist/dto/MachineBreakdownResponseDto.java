package pl.edu.pk.pkampus.modules.receptionist.dto;

import java.util.UUID;

public record MachineBreakdownResponseDto(
        UUID machineId,
        int cancelledCount,
        UUID issueId
) {
}
