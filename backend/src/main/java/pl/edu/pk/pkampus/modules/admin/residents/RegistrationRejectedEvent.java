package pl.edu.pk.pkampus.modules.admin.residents;

import java.util.UUID;

/** Published when a residency application is rejected (account deleted). Email goes out after commit. */
public record RegistrationRejectedEvent(UUID residentId, String email, String firstName, String reason) {
}
