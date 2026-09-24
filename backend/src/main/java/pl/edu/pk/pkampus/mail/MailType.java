package pl.edu.pk.pkampus.mail;

/**
 * Notification categories used as metric tags for the {@code mail.sent} and
 * {@code mail.failed} counters. Tags are stable identifiers (never PII), so
 * operators can tell an auth-critical delivery failure apart from a
 * best-effort notification failure.
 */
enum MailType {

    VERIFICATION("verification", true),
    PASSWORD_RESET("password-reset", true),
    ACCOUNT_ACTIVATED("account-activated", false),
    REGISTRATION_REJECTED("registration-rejected", false),
    LAUNDRY_BREAKDOWN("laundry-breakdown", false),
    ROOM_MAINTENANCE("room-maintenance", false),
    ISSUE_STATUS_CHANGED("issue-status-changed", false),
    BOOKING_AUTO_CANCELLED("booking-auto-cancelled", false),
    ACCOUNT_BLOCKED("account-blocked", false),
    CHECKED_OUT("checked-out", false),
    ROOM_BAN("room-ban", false);

    private final String tag;
    private final boolean critical;

    MailType(String tag, boolean critical) {
        this.tag = tag;
        this.critical = critical;
    }

    String tag() {
        return tag;
    }

    boolean critical() {
        return critical;
    }
}
