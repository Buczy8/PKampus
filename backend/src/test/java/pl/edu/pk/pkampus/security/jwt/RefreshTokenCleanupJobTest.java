package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenCleanupJob cleanupJob;

    @Test
    void shouldDelegateDeleteToRepository() {
        when(refreshTokenRepository.deleteStaleTokens(any(Instant.class), any(Instant.class))).thenReturn(3);

        cleanupJob.cleanupStaleRefreshTokens();

        verify(refreshTokenRepository).deleteStaleTokens(any(Instant.class), any(Instant.class));
    }
}
