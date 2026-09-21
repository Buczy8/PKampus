package pl.edu.pk.pkampus.modules.receptionist.dto;

import java.util.List;

public record StaffRoomScheduleResponseDto(
        List<StaffRoomDto> rooms,
        List<StaffRoomScheduleDayDto> days
) {
}
