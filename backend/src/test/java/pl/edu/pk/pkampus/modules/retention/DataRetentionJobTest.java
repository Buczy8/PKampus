package pl.edu.pk.pkampus.modules.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataRetentionJob POJO/Mockito unit tests (AAA)")
class DataRetentionJobTest {

    @Mock
    private DataRetentionService dataRetentionService;

    @InjectMocks
    private DataRetentionJob dataRetentionJob;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataRetentionJob, "enabled", true);
    }

    @Test
    @DisplayName("runScheduledRetention invokes runRetentionTasks when enabled")
    void runScheduledRetentionEnabled() {
        // Arrange & Act
        dataRetentionJob.runScheduledRetention();

        // Assert
        verify(dataRetentionService).runRetentionTasks();
    }

    @Test
    @DisplayName("runScheduledRetention catches exception from service without rethrowing")
    void runScheduledRetentionCatchesException() {
        // Arrange
        doThrow(new RuntimeException("Database error")).when(dataRetentionService).runRetentionTasks();

        // Act & Assert (should not throw)
        dataRetentionJob.runScheduledRetention();
        verify(dataRetentionService).runRetentionTasks();
    }

    @Test
    @DisplayName("runScheduledRetention does nothing when disabled")
    void runScheduledRetentionDisabled() {
        // Arrange
        ReflectionTestUtils.setField(dataRetentionJob, "enabled", false);

        // Act
        dataRetentionJob.runScheduledRetention();

        // Assert
        verify(dataRetentionService, never()).runRetentionTasks();
    }
}
