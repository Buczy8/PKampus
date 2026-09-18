package pl.edu.pk.pkampus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceptionistDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private UserStatus status;
    private UUID dormitoryId;
    private String dormitoryName;
    private Instant createdAt;
}
