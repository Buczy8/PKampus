package pl.edu.pk.pkampus.modules.admin.residents;

import java.util.UUID;

/** Published when a residency application is approved. Email goes out after commit. */
public record ResidentActivatedEvent(UUID userId, String email, String firstName, String roomNumber, String dormitoryName) {
}
