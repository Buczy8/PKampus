package pl.edu.pk.pkampus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.dormitory.Room;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DormRoomDto {

    private UUID id;
    private UUID dormitoryId;
    private String roomNumber;
    private Integer floor;
    private Integer capacity;
    private Instant createdAt;

    public static DormRoomDto from(Room room) {
        return DormRoomDto.builder()
                .id(room.getId())
                .dormitoryId(room.getDormitory().getId())
                .roomNumber(room.getRoomNumber())
                .floor(room.getFloor())
                .capacity(room.getCapacity())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
