package pl.edu.pk.pkampus.modules.auth;

/**
 * Published when a password-reset token is persisted.
 * The reset email is sent by {@link PasswordResetMailListener} after commit,
 * so a rolled-back request never produces a phantom email.
 */
public record PasswordResetRequestedEvent(String email, String firstName, String rawToken) {
}
