package pl.edu.pk.pkampus.modules.admin.residents;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.edu.pk.pkampus.mail.EmailService;

@Component
@RequiredArgsConstructor
class AdminMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResidentActivated(ResidentActivatedEvent event) {
        emailService.sendAccountActivatedEmail(event.email(), event.firstName(), event.roomNumber(), event.dormitoryName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationRejected(RegistrationRejectedEvent event) {
        emailService.sendRegistrationRejectedEmail(event.email(), event.firstName(), event.reason());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResidentStatus(ResidentStatusEvent event) {
        switch (event.type()) {
            case BLOCKED -> emailService.sendAccountBlockedEmail(event.email(), event.firstName());
            case CHECKED_OUT -> emailService.sendCheckedOutEmail(event.email(), event.firstName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomBanIssued(RoomBanIssuedEvent event) {
        emailService.sendRoomBanEmail(event.email(), event.firstName(), event.start(), event.end(), event.reason());
    }
}
