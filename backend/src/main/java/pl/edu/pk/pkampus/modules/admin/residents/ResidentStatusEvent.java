package pl.edu.pk.pkampus.modules.admin.residents;

import java.util.UUID;

/** Published when a resident is blocked, checked out, or issued a room ban. Emails go out after commit. */
public record ResidentStatusEvent(Type type, UUID residentId, String email, String firstName) {

    public enum Type {
        BLOCKED,
        CHECKED_OUT
    }
}
