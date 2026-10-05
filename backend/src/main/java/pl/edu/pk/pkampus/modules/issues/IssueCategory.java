package pl.edu.pk.pkampus.modules.issues;

public enum IssueCategory {
    PLUMBING,
    ELECTRICAL,
    FURNITURE,
    /**
     * Valid API value offered by the frontend issue form; no backend flow
     * special-cases it, hence the unused warning is a false positive.
     */
    @SuppressWarnings("unused")
    LOCKSMITH,
    OTHER
}
