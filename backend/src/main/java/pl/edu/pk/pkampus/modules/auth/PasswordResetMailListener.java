package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.edu.pk.pkampus.mail.EmailService;

@Component
@RequiredArgsConstructor
class PasswordResetMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        emailService.sendPasswordResetEmail(event.email(), event.firstName(), event.rawToken());
    }
}
