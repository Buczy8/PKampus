package pl.edu.pk.pkampus.modules.laundry.dto;

import java.time.LocalDate;
import java.util.List;

public record LaundryScheduleDayDto(
        LocalDate date,
        List<LaundrySlotDto> slots
) {
}
