package pl.edu.pk.pkampus.modules.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.mail.ResourceSchedulePage;

/**
 * Sends 15-minute auto-cancellation notice e-mails only after the triggering
 * transaction commits, so residents never receive mail about cancellations
 * that were rolled back. Owns the mapping from the domain {@link ResourceKind}
 * to the resident-facing schedule page — domain events stay presentation-free.
 */
@Component
@RequiredArgsConstructor
class BookingMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingAutoCancelled(BookingAutoCancelledEvent event) {
        emailService.sendBookingAutoCancelled15MinEmail(
                event.email(), event.firstName(), event.resourceName(),
                event.startTime(), schedulePageFor(event.kind()));
    }

    private static ResourceSchedulePage schedulePageFor(ResourceKind kind) {
        return switch (kind) {
            case LAUNDRY -> ResourceSchedulePage.LAUNDRY;
            case ROOM -> ResourceSchedulePage.ROOMS;
        };
    }
}
