package pl.edu.pk.pkampus.modules.receptionist.dto;

import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffIssueDto(
        UUID id,
        String locationLabel,
        String roomNumber,
        Integer floor,
        String commonAreaName,
        IssueCategory category,
        IssueUrgency urgency,
        String description,
        IssueStatus status,
        String staffNotes,
        boolean hasPhoto,
        String photoUrl,
        String reporterFirstName,
        String reporterLastName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
