package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.modules.auth.passwordreset.PasswordResetTokenRepository;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private RefreshTokenCleanupJob cleanupJob;

    @Test
    void shouldDelegateDeleteToBothRepositoriesWhenTokensFound() {
        // Arrange
        when(refreshTokenRepository.deleteStaleTokens(any(Instant.class), any(Instant.class))).thenReturn(5);
        when(passwordResetTokenRepository.deleteStaleTokens(any(Instant.class))).thenReturn(2);

        // Act
        cleanupJob.cleanupStaleRefreshTokens();

        // Assert
        verify(refreshTokenRepository).deleteStaleTokens(any(Instant.class), any(Instant.class));
        verify(passwordResetTokenRepository).deleteStaleTokens(any(Instant.class));
    }

    @Test
    void shouldExecuteWithoutErrorWhenNoTokensToDelete() {
        // Arrange
        when(refreshTokenRepository.deleteStaleTokens(any(Instant.class), any(Instant.class))).thenReturn(0);
        when(passwordResetTokenRepository.deleteStaleTokens(any(Instant.class))).thenReturn(0);

        // Act
        cleanupJob.cleanupStaleRefreshTokens();

        // Assert
        verify(refreshTokenRepository).deleteStaleTokens(any(Instant.class), any(Instant.class));
        verify(passwordResetTokenRepository).deleteStaleTokens(any(Instant.class));
    }
}
