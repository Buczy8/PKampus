package pl.edu.pk.pkampus.mail;

/**
 * Published when a booking is released by the 15-minute no-show rule (BR-02).
 * Consumed after commit by {@link DeskMailListener}.
 */
public record BookingAutoCancelledEvent(
        String email,
        String firstName,
        String resourceName,
        String startTimeLabel,
        ResourceSchedulePage page
) {
}
