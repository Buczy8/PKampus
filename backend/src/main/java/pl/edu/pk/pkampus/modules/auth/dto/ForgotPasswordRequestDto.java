package pl.edu.pk.pkampus.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to initiate password reset flow")
public class ForgotPasswordRequestDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(example = "jan.kowalski@student.pk.edu.pl", description = "User's registered email address")
    private String email;
}
