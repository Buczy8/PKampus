package pl.edu.pk.pkampus.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeskMailListener unit tests (AAA)")
class DeskMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private DeskMailListener listener;

    @Test
    @DisplayName("Should delegate booking auto-cancelled events to EmailService")
    void onBookingAutoCancelledDelegates() {
        // Arrange
        BookingAutoCancelledEvent event = new BookingAutoCancelledEvent(
                "student@pk.edu.pl", "Kamil", "Laundry machine PRALKA-01",
                "23.09.2026 14:00", ResourceSchedulePage.LAUNDRY);

        // Act
        listener.onBookingAutoCancelled(event);

        // Assert
        verify(emailService).sendBookingAutoCancelled15MinEmail(
                "student@pk.edu.pl", "Kamil", "Laundry machine PRALKA-01",
                "23.09.2026 14:00", ResourceSchedulePage.LAUNDRY);
    }

    @Test
    @DisplayName("Should delegate room maintenance events to EmailService")
    void onRoomMaintenanceDelegates() {
        // Arrange
        RoomMaintenanceNoticeEvent event = new RoomMaintenanceNoticeEvent(
                "student@pk.edu.pl", "Kamil", "Salka Muzyczna", "23.09.2026 18:00");

        // Act
        listener.onRoomMaintenance(event);

        // Assert
        verify(emailService).sendRoomMaintenanceEmail(
                "student@pk.edu.pl", "Kamil", "Salka Muzyczna", "23.09.2026 18:00");
    }

    @Test
    @DisplayName("Should delegate laundry breakdown events to EmailService")
    void onLaundryBreakdownDelegates() {
        // Arrange
        LaundryBreakdownNoticeEvent event = new LaundryBreakdownNoticeEvent(
                "student@pk.edu.pl", "Kamil", "Pralka #3", "23.09.2026 14:00");

        // Act
        listener.onLaundryBreakdown(event);

        // Assert
        verify(emailService).sendLaundryMachineBreakdownEmail(
                "student@pk.edu.pl", "Kamil", "Pralka #3", "23.09.2026 14:00");
    }
}
