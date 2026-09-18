package pl.edu.pk.pkampus.modules.superadmin.dto;

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
public class CreateDormAdminRequestDto {

    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 80)
    private String lastName;

    @NotBlank
    @Size(max = 150)
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^\\+?[0-9]{9,15}$",
            message = "Phone number must be 9-15 digits, optionally starting with +"
    )
    private String phoneNumber;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = "Password must be at least 8 characters long and contain an uppercase letter, a digit, and a special character"
    )
    private String password;

    @NotNull
    private UUID dormitoryId;
}
