package pl.edu.pk.pkampus.modules.booking;

import java.time.Instant;

/**
 * Published when a booking is released by the 15-minute no-show rule (BR-02).
 * Consumed after commit by {@link BookingMailListener}.
 */
public record BookingAutoCancelledEvent(
        String email,
        String firstName,
        String resourceName,
        Instant startTime,
        ResourceKind kind
) {
}
