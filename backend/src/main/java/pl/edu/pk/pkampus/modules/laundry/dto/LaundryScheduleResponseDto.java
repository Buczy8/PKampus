package pl.edu.pk.pkampus.modules.laundry.dto;

import java.time.LocalTime;
import java.util.List;

public record LaundryScheduleResponseDto(
        LocalTime openingTime,
        LocalTime closingTime,
        int slotDurationMinutes,
        List<LaundryMachineDto> machines,
        List<LaundryScheduleDayDto> days
) {
}
