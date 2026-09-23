package pl.edu.pk.pkampus.modules.auth;

import java.util.UUID;

/**
 * Published when a resident account is persisted.
 * The verification email is sent by {@link RegistrationMailListener} after commit,
 * so a rolled-back registration never produces a phantom email.
 */
public record ResidentRegisteredEvent(UUID userId, String email) {
}
