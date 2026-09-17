package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

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
        UUID userId = UUID.randomUUID();

        tokenRevocationService.revokeUser(userId);

        assertTrue(tokenRevocationService.isRevoked(userId));
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(authenticatedUserCache).invalidate(userId);
    }

    @Test
    void shouldOnlyBlacklistAccessOnBlacklistAccessToken() {
        UUID userId = UUID.randomUUID();

        tokenRevocationService.blacklistAccessToken(userId);

        assertTrue(tokenRevocationService.isRevoked(userId));
        verify(authenticatedUserCache).invalidate(userId);
    }
}
