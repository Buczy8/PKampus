package pl.edu.pk.pkampus.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.UserStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailResponseDto {

    private String message;
    private UserStatus status;
}
