package pl.edu.pk.pkampus.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends desk-notification e-mails only after the triggering transaction commits,
 * so residents never receive mail about bookings that were rolled back.
 */
@Component
@RequiredArgsConstructor
class DeskMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingAutoCancelled(BookingAutoCancelledEvent event) {
        emailService.sendBookingAutoCancelled15MinEmail(
                event.email(), event.firstName(), event.resourceName(),
                event.startTimeLabel(), event.page());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomMaintenance(RoomMaintenanceNoticeEvent event) {
        emailService.sendRoomMaintenanceEmail(
                event.email(), event.firstName(), event.roomName(), event.startTimeLabel());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLaundryBreakdown(LaundryBreakdownNoticeEvent event) {
        emailService.sendLaundryMachineBreakdownEmail(
                event.email(), event.firstName(), event.machineIdentifier(), event.startTimeLabel());
    }
}
