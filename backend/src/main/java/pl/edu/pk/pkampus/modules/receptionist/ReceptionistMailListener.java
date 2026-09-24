package pl.edu.pk.pkampus.modules.receptionist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.edu.pk.pkampus.mail.EmailService;

/**
 * Sends desk-notification e-mails (maintenance and breakdowns) only after
 * the triggering transaction commits, so residents never receive mail about
 * cancellations that were rolled back.
 */
@Component
@RequiredArgsConstructor
class ReceptionistMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomMaintenance(RoomMaintenanceNoticeEvent event) {
        emailService.sendRoomMaintenanceEmail(
                event.email(), event.firstName(), event.roomName(), event.startTime());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLaundryBreakdown(LaundryBreakdownNoticeEvent event) {
        emailService.sendLaundryMachineBreakdownEmail(
                event.email(), event.firstName(), event.machineIdentifier(), event.startTime());
    }
}
