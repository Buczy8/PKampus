package pl.edu.pk.pkampus.modules.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationMailListenerTest {

    @Mock
    private SignedEmailTokenService signedEmailTokenService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private RegistrationMailListener listener;

    @Test
    void shouldSendVerificationEmailForRegisteredEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(signedEmailTokenService.generateToken(userId, "student@pk.edu.pl"))
                .thenReturn("signed-token-xyz");

        // Act
        listener.onResidentRegistered(new ResidentRegisteredEvent(userId, "student@pk.edu.pl"));

        // Assert
        verify(emailService).sendVerificationEmail(eq("student@pk.edu.pl"), eq("signed-token-xyz"));
    }
}
