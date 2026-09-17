package pl.edu.pk.pkampus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresInSeconds;

    private UserProfileDto user;
}
