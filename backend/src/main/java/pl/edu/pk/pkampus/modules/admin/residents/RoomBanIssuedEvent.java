package pl.edu.pk.pkampus.modules.admin.residents;

import java.time.LocalDate;
import java.util.UUID;

/** Published when a ROOM_BAN sanction is issued. Email goes out after commit. */
public record RoomBanIssuedEvent(UUID residentId, String email, String firstName, LocalDate start, LocalDate end, String reason) {
}
