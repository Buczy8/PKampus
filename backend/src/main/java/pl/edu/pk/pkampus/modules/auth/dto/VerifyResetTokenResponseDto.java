package pl.edu.pk.pkampus.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token verification response for password reset form")
public class VerifyResetTokenResponseDto {

    @Schema(example = "true", description = "Whether the reset token is currently valid and unused")
    private boolean valid;

    @Schema(example = "j***@student.pk.edu.pl", description = "Masked email address of the account")
    private String maskedEmail;
}
