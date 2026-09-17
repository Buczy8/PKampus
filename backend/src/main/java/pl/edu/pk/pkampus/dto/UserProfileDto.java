package pl.edu.pk.pkampus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.model.UserRole;
import pl.edu.pk.pkampus.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String avatarUrl;
    private UserRole role;
    private UserStatus status;
    private UUID dormitoryId;
    private String dormitoryName;
    private String roomNumber;
    private Instant createdAt;
}
