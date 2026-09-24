package pl.edu.pk.pkampus.modules.receptionist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceptionistMailListener unit tests (AAA)")
class ReceptionistMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReceptionistMailListener listener;

    @Test
    @DisplayName("Should delegate room maintenance events to EmailService")
    void onRoomMaintenanceDelegates() {
        // Arrange
        Instant startTime = Instant.parse("2026-09-23T16:00:00Z");
        RoomMaintenanceNoticeEvent event = new RoomMaintenanceNoticeEvent(
                "student@pk.edu.pl", "Kamil", "Salka Muzyczna", startTime);

        // Act
        listener.onRoomMaintenance(event);

        // Assert
        verify(emailService).sendRoomMaintenanceEmail(
                "student@pk.edu.pl", "Kamil", "Salka Muzyczna", startTime);
    }

    @Test
    @DisplayName("Should delegate laundry breakdown events to EmailService")
    void onLaundryBreakdownDelegates() {
        // Arrange
        Instant startTime = Instant.parse("2026-09-23T12:00:00Z");
        LaundryBreakdownNoticeEvent event = new LaundryBreakdownNoticeEvent(
                "student@pk.edu.pl", "Kamil", "Pralka #3", startTime);

        // Act
        listener.onLaundryBreakdown(event);

        // Assert
        verify(emailService).sendLaundryMachineBreakdownEmail(
                "student@pk.edu.pl", "Kamil", "Pralka #3", startTime);
    }
}
