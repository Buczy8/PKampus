package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;

@Component
@RequiredArgsConstructor
class RegistrationMailListener {

    private final SignedEmailTokenService signedEmailTokenService;
    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResidentRegistered(ResidentRegisteredEvent event) {
        String token = signedEmailTokenService.generateToken(event.userId(), event.email());
        emailService.sendVerificationEmail(event.email(), token);
    }
}
