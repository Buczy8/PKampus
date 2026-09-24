package pl.edu.pk.pkampus.modules.receptionist;

import java.time.Instant;

/**
 * Published per affected resident when a thematic room goes under maintenance.
 * Consumed after commit by {@link ReceptionistMailListener}.
 */
public record RoomMaintenanceNoticeEvent(
        String email,
        String firstName,
        String roomName,
        Instant startTime
) {
}
