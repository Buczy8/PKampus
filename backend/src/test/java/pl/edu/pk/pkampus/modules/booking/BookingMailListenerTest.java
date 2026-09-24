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
import java.time.temporal.ChronoUnit;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingMailListener unit tests (AAA)")
class BookingMailListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private BookingMailListener listener;

    @Test
    @DisplayName("Should map LAUNDRY auto-cancellations to the laundry schedule page")
    void onBookingAutoCancelledDelegatesLaundry() {
        // Arrange
        Instant startTime = Instant.now().plus(2, ChronoUnit.HOURS);
        BookingAutoCancelledEvent event = new BookingAutoCancelledEvent(
                "student@pk.edu.pl", "Kamil", "Laundry machine PRALKA-01",
                startTime, ResourceKind.LAUNDRY);

        // Act
        listener.onBookingAutoCancelled(event);

        // Assert
        verify(emailService).sendBookingAutoCancelled15MinEmail(
                "student@pk.edu.pl", "Kamil", "Laundry machine PRALKA-01",
                startTime, ResourceSchedulePage.LAUNDRY);
    }

    @Test
    @DisplayName("Should map ROOM auto-cancellations to the rooms schedule page")
    void onBookingAutoCancelledDelegatesRoom() {
        // Arrange
        Instant startTime = Instant.now().plus(2, ChronoUnit.HOURS);
        BookingAutoCancelledEvent event = new BookingAutoCancelledEvent(
                "student@pk.edu.pl", "Kamil", "Room Salka Muzyczna",
                startTime, ResourceKind.ROOM);

        // Act
        listener.onBookingAutoCancelled(event);

        // Assert
        verify(emailService).sendBookingAutoCancelled15MinEmail(
                "student@pk.edu.pl", "Kamil", "Room Salka Muzyczna",
                startTime, ResourceSchedulePage.ROOMS);
    }
}
