package pl.edu.pk.pkampus.modules.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.UserStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReceptionistRequestDto {

    private UserStatus status;

    @Size(max = 50)
    private String firstName;

    @Size(max = 80)
    private String lastName;

    @Size(max = 20)
    private String phoneNumber;
}
