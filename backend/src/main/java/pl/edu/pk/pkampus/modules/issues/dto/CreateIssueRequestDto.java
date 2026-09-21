package pl.edu.pk.pkampus.modules.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueLocationType;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;

public record CreateIssueRequestDto(
        @NotNull IssueLocationType locationType,
        @Size(max = 100) String commonAreaName,
        @NotNull IssueCategory category,
        @NotNull IssueUrgency urgency,
        @NotBlank @Size(max = 4000) String description
) {
}
