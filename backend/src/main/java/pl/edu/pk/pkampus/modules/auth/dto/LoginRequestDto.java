package pl.edu.pk.pkampus.modules.auth.dto;

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
public class LoginRequestDto {

    @NotBlank(message = "Adres e-mail jest wymagany")
    @Email(message = "Niepoprawny format adresu e-mail")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    private String password;
}
