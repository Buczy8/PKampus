package pl.edu.pk.pkampus.modules.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.mail.ResourceSchedulePage;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingMailListener unit tests (AAA)")
class BookingMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private BookingMailListener listener;

    @Test
    @DisplayName("Should delegate booking auto-cancelled events to EmailService")
    void onBookingAutoCancelledDelegates() {
        // Arrange
        Instant startTime = Instant.parse("2026-09-23T12:00:00Z");
        BookingAutoCancelledEvent event = new BookingAutoCancelledEvent(
                "student@pk.edu.pl", "Kamil", "Laundry machine PRALKA-01",
                startTime, ResourceSchedulePage.LAUNDRY);

        // Act
        listener.onBookingAutoCancelled(event);

        // Assert
        verify(emailService).sendBookingAutoCancelled15MinEmail(
                "student@pk.edu.pl", "Kamil", "Laundry machine PRALKA-01",
                startTime, ResourceSchedulePage.LAUNDRY);
    }
}
