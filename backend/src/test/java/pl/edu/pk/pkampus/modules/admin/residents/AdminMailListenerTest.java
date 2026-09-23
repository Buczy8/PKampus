package pl.edu.pk.pkampus.modules.admin.residents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.admin.residents.ResidentStatusEvent.Type;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AdminMailListener listener;

    @Test
    void shouldSendActivatedEmail() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        listener.onResidentActivated(new ResidentActivatedEvent(userId, "a@pk.edu.pl", "Jan", "101", "DS-1"));

        // Assert
        verify(emailService).sendAccountActivatedEmail(eq("a@pk.edu.pl"), eq("Jan"), eq("101"), eq("DS-1"));
    }

    @Test
    void shouldSendRejectedEmail() {
        // Arrange
        UUID residentId = UUID.randomUUID();

        // Act
        listener.onRegistrationRejected(new RegistrationRejectedEvent(residentId, "a@pk.edu.pl", "Jan", "Nope"));

        // Assert
        verify(emailService).sendRegistrationRejectedEmail(eq("a@pk.edu.pl"), eq("Jan"), eq("Nope"));
    }

    @Test
    void shouldSendBlockedAndCheckedOutEmails() {
        // Arrange
        UUID residentId = UUID.randomUUID();

        // Act
        listener.onResidentStatus(new ResidentStatusEvent(Type.BLOCKED, residentId, "a@pk.edu.pl", "Jan"));
        listener.onResidentStatus(new ResidentStatusEvent(Type.CHECKED_OUT, residentId, "a@pk.edu.pl", "Jan"));

        // Assert
        verify(emailService).sendAccountBlockedEmail(eq("a@pk.edu.pl"), eq("Jan"));
        verify(emailService).sendCheckedOutEmail(eq("a@pk.edu.pl"), eq("Jan"));
    }

    @Test
    void shouldSendRoomBanEmail() {
        // Arrange
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = start.plusMonths(2);
        RoomBanIssuedEvent event = new RoomBanIssuedEvent(
                UUID.randomUUID(), "a@pk.edu.pl", "Jan", start, end, "Noise");

        // Act
        listener.onRoomBanIssued(event);

        // Assert
        verify(emailService).sendRoomBanEmail(
                eq("a@pk.edu.pl"), eq("Jan"), eq(start), eq(end), eq("Noise"));
    }
}
