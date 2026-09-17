package pl.edu.pk.pkampus.modules.admin.dto;

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
public class ActivateResidentResponseDto {

    private UUID userId;
    private UserStatus status;
    private UUID roomAssignmentId;
    private String roomNumber;
    private String academicYear;
    private String message;
}
