package pl.edu.pk.pkampus.modules.issues.dto;

import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssueDto(
        UUID id,
        String locationLabel,
        UUID roomId,
        String commonAreaName,
        IssueCategory category,
        IssueUrgency urgency,
        String description,
        IssueStatus status,
        String staffNotes,
        boolean hasPhoto,
        String photoUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
