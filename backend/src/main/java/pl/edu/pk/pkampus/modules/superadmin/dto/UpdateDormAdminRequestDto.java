package pl.edu.pk.pkampus.modules.superadmin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDormAdminRequestDto {

    private UUID dormitoryId;

    private UserStatus status;

    @Size(max = 50)
    private String firstName;

    @Size(max = 80)
    private String lastName;

    @Size(max = 20)
    private String phoneNumber;
}
