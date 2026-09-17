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
public class PendingResidentDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String declaredRoomNumber;
    private UUID dormitoryId;
    private String dormitoryName;
    private String avatarUrl;
    private Instant createdAt;
}
