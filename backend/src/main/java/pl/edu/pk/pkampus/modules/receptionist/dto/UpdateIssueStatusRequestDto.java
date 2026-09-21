package pl.edu.pk.pkampus.modules.receptionist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;

public record UpdateIssueStatusRequestDto(
        @NotNull IssueStatus status,
        @Size(max = 4000) String staffNotes
) {
}
