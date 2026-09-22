package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthenticatedUserCache authenticatedUserCache;

    private TokenRevocationService tokenRevocationService;

    @BeforeEach
    void setUp() {
        tokenRevocationService = new TokenRevocationService(15L, refreshTokenRepository, authenticatedUserCache);
    }

    @Test
    void shouldBlacklistAndInvalidateCacheOnRevokeUser() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        tokenRevocationService.revokeUser(userId);

        // Assert
        assertTrue(tokenRevocationService.isRevoked(userId));
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(authenticatedUserCache).invalidate(userId);
    }

    @Test
    void shouldOnlyBlacklistAccessOnBlacklistAccessToken() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        tokenRevocationService.blacklistAccessToken(userId);

        // Assert
        assertTrue(tokenRevocationService.isRevoked(userId));
        verify(authenticatedUserCache).invalidate(userId);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void shouldClearRevocationSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        tokenRevocationService.blacklistAccessToken(userId);
        assertTrue(tokenRevocationService.isRevoked(userId));

        // Act
        tokenRevocationService.clearRevocation(userId);

        // Assert
        assertFalse(tokenRevocationService.isRevoked(userId));
    }

    @Test
    void shouldReturnFalseForIsRevokedWhenUserNotRevokedOrNull() {
        // Arrange & Act & Assert
        assertFalse(tokenRevocationService.isRevoked(null));
        assertFalse(tokenRevocationService.isRevoked(UUID.randomUUID()));
    }

    @Test
    void shouldHandleNullUserIdGracefullyInAllMethods() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> tokenRevocationService.revokeUser(null));
        assertDoesNotThrow(() -> tokenRevocationService.blacklistAccessToken(null));
        assertDoesNotThrow(() -> tokenRevocationService.clearRevocation(null));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        verify(authenticatedUserCache, never()).invalidate(any());
    }
}
