package pl.edu.pk.pkampus.modules.auth.passwordreset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetMailListener listener;

    @Test
    void shouldSendResetEmailForRequestedEvent() {
        // Arrange
        PasswordResetRequestedEvent event =
                new PasswordResetRequestedEvent("student@pk.edu.pl", "Jan", "raw-token-abc");

        // Act
        listener.onPasswordResetRequested(event);

        // Assert
        verify(emailService).sendPasswordResetEmail(eq("student@pk.edu.pl"), eq("Jan"), eq("raw-token-abc"));
    }
}
