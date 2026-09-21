package pl.edu.pk.pkampus.modules.receptionist.dto;

import java.time.LocalDate;
import java.util.List;

public record StaffRoomScheduleDayDto(
        LocalDate date,
        List<StaffRoomBookingSlotDto> bookings
) {
}
