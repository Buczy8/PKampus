package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeskOpenIssueDto(
        UUID id,
        String locationLabel,
        IssueCategory category,
        IssueUrgency urgency,
        String description,
        IssueStatus status,
        OffsetDateTime createdAt
) {
}
