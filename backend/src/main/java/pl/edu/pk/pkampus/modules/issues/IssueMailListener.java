package pl.edu.pk.pkampus.modules.issues;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.edu.pk.pkampus.mail.EmailService;

/**
 * Notifies the reporter only after the status-change transaction commits,
 * so residents never receive mail about updates that were rolled back.
 */
@Component
@RequiredArgsConstructor
class IssueMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueStatusChanged(IssueStatusChangedEvent event) {
        emailService.sendIssueStatusChangedEmail(
                event.email(), event.firstName(), event.statusLabel(), event.staffNotes());
    }
}
