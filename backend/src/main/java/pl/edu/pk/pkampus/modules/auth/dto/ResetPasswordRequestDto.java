package pl.edu.pk.pkampus.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to complete password reset using one-time token")
public class ResetPasswordRequestDto {

    @NotBlank(message = "Token is required")
    @Schema(description = "Raw cryptographic token received via email link")
    private String token;

    @NotBlank(message = "New password is required")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$",
            message = "Password must be at least 8 characters long and contain an uppercase letter, a digit, and a special character"
    )
    @Schema(example = "NewSecretPass123!", description = "New password conforming to NFR-SEC-02 complexity rules")
    private String newPassword;
}
