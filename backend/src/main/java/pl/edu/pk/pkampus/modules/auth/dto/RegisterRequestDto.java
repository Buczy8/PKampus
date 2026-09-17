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

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email address format")
    @Size(max = 150, message = "Email address cannot exceed 150 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$",
            message = "Password must be at least 8 characters long and contain an uppercase letter, a digit, and a special character"
    )
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 80, message = "Last name cannot exceed 80 characters")
    private String lastName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^\\+?[0-9]{9,15}$",
            message = "Phone number must contain between 9 and 15 digits (optional '+' prefix)"
    )
    private String phoneNumber;

    @NotNull(message = "Dormitory ID is required")
    private UUID dormitoryId;

    @NotBlank(message = "Declared room number is required")
    @Size(max = 10, message = "Room number cannot exceed 10 characters")
    private String declaredRoomNumber;
}
