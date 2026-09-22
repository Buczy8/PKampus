package pl.edu.pk.pkampus.modules.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingAutoCancellationJob unit tests (AAA)")
class BookingAutoCancellationJobTest {

    @Mock
    private BookingAutoCancellationService autoCancellationService;

    @InjectMocks
    private BookingAutoCancellationJob job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "thresholdMinutes", 15);
    }

    @Test
    @DisplayName("Should invoke autoCancellationService when enabled is true")
    void runJobWhenEnabled() {
        // Arrange (configured in setUp)

        // Act
        job.runAutoCancellation();

        // Assert
        verify(autoCancellationService).cancelAllExpiredBookings(15);
    }

    @Test
    @DisplayName("Should do nothing when enabled is false")
    void runJobWhenDisabled() {
        // Arrange
        ReflectionTestUtils.setField(job, "enabled", false);

        // Act
        job.runAutoCancellation();

        // Assert
        verify(autoCancellationService, never()).cancelAllExpiredBookings(anyInt());
    }

    @Test
    @DisplayName("Should catch and log exceptions without propagating")
    void handleExceptionGracefully() {
        // Arrange
        when(autoCancellationService.cancelAllExpiredBookings(15))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        assertDoesNotThrow(() -> job.runAutoCancellation());
        verify(autoCancellationService).cancelAllExpiredBookings(15);
    }
}
