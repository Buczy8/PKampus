package pl.edu.pk.pkampus.modules.issues;

/**
 * Published when staff changes an issue status.
 * Consumed after commit by {@link IssueMailListener}.
 */
public record IssueStatusChangedEvent(
        String email,
        String firstName,
        String statusLabel,
        String staffNotes
) {
}
