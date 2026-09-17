package pl.edu.pk.pkampus.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresInSeconds;

    private String refreshToken;

    private long refreshExpiresInSeconds;

    private UserProfileDto user;
}
