package pl.edu.pk.pkampus.modules.receptionist;

import java.time.Instant;

/**
 * Published per affected resident when a laundry machine goes out of order.
 * Consumed after commit by {@link ReceptionistMailListener}.
 */
public record LaundryBreakdownNoticeEvent(
        String email,
        String firstName,
        String machineIdentifier,
        Instant startTime
) {
}
