package pl.edu.pk.pkampus.modules.issues;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueMailListener unit tests (AAA)")
class IssueMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private IssueMailListener listener;

    @Test
    @DisplayName("Should delegate issue status changed events to EmailService")
    void onIssueStatusChangedDelegates() {
        // Arrange
        IssueStatusChangedEvent event = new IssueStatusChangedEvent(
                "student@pk.edu.pl", "Kamil", "IN_PROGRESS", "Plumber at 10:00");

        // Act
        listener.onIssueStatusChanged(event);

        // Assert
        verify(emailService).sendIssueStatusChangedEmail(
                "student@pk.edu.pl", "Kamil", "IN_PROGRESS", "Plumber at 10:00");
    }
}
