package pl.edu.pk.pkampus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
