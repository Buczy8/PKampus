package pl.edu.pk.pkampus.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDto {

    @NotBlank(message = "Adres e-mail jest wymagany")
    @Email(message = "Niepoprawny format adresu e-mail")
    @Size(max = 150, message = "Adres e-mail może mieć maksymalnie 150 znaków")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$",
            message = "Hasło musi mieć co najmniej 8 znaków, zawierać dużą literę, cyfrę oraz znak specjalny"
    )
    private String password;

    @NotBlank(message = "Imię jest wymagane")
    @Size(max = 50, message = "Imię może mieć maksymalnie 50 znaków")
    private String firstName;

    @NotBlank(message = "Nazwisko jest wymagane")
    @Size(max = 80, message = "Nazwisko może mieć maksymalnie 80 znaków")
    private String lastName;

    @NotBlank(message = "Numer telefonu jest wymagany")
    @Pattern(
            regexp = "^\\+?[0-9]{9,15}$",
            message = "Numer telefonu musi zawierać od 9 do 15 cyfr (opcjonalnie z prefiksem '+')"
    )
    private String phoneNumber;

    @NotNull(message = "Identyfikator akademika jest wymagany")
    private UUID dormitoryId;

    @NotBlank(message = "Deklarowany numer pokoju jest wymagany")
    @Size(max = 10, message = "Numer pokoju może mieć maksymalnie 10 znaków")
    private String declaredRoomNumber;
}
