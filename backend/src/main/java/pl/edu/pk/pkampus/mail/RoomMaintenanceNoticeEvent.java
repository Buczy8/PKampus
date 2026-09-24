package pl.edu.pk.pkampus.mail;

/**
 * Published per affected resident when a thematic room goes under maintenance.
 * Consumed after commit by {@link DeskMailListener}.
 */
public record RoomMaintenanceNoticeEvent(
        String email,
        String firstName,
        String roomName,
        String startTimeLabel
) {
}
