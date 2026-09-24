package pl.edu.pk.pkampus.mail;

/**
 * Published per affected resident when a laundry machine goes out of order.
 * Consumed after commit by {@link DeskMailListener}.
 */
public record LaundryBreakdownNoticeEvent(
        String email,
        String firstName,
        String machineIdentifier,
        String startTimeLabel
) {
}
