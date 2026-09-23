package pl.edu.pk.pkampus.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

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

    public static UserProfileDto from(User user, String roomNumber) {
        UUID dormId = null;
        String dormName = null;
        if (user.getDormitory() != null) {
            dormId = user.getDormitory().getId();
            dormName = user.getDormitory().getName();
        }

        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .dormitoryId(dormId)
                .dormitoryName(dormName)
                .roomNumber(roomNumber)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
