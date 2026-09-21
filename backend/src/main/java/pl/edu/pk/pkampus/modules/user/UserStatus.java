package pl.edu.pk.pkampus.modules.user;

public enum UserStatus {
    PENDING_EMAIL,
    PENDING_APPROVAL,
    MUST_CHANGE_PASSWORD,
    ACTIVE,
    BLOCKED,
    CHECKED_OUT
}
