package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.common.exception.InvalidTokenException;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationDays", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(refreshTokenService, "maxActiveRefreshTokens", 5);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldCreateRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.countByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(testUser.getId()), any()))
                .thenReturn(1L);

        String rawToken = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void shouldRotateRefreshTokenSuccessfully() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.countByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(testUser.getId()), any()))
                .thenReturn(1L);
        String rawToken = refreshTokenService.createRefreshToken(testUser);

        RefreshToken existingToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        RefreshTokenService.RefreshTokenResult result = refreshTokenService.rotateRefreshToken(rawToken);

        assertNotNull(result);
        assertNotNull(result.newRawToken());
        assertEquals(testUser, result.user());
        assertTrue(existingToken.isRevoked());
        assertNotNull(existingToken.getReplacedByToken());
    }

    @Test
    void shouldRejectRevokedTokenWithoutRevokingOtherSessions() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotateRefreshToken("reused-token"));

        assertTrue(ex.getMessage().contains("no longer valid"));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void shouldFailWhenTokenIsExpired() {
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotateRefreshToken("expired-token"));

        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void shouldRevokeRefreshToken() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        Optional<UUID> ownerId = refreshTokenService.revokeRefreshToken("raw-token");

        assertTrue(token.isRevoked());
        assertTrue(ownerId.isPresent());
        assertEquals(testUser.getId(), ownerId.get());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldEnforceMaxActiveSessionsByRevokingOldest() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.countByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(testUser.getId()), any()))
                .thenReturn(6L);

        RefreshToken oldest = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("old")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
                eq(testUser.getId()), any()))
                .thenReturn(java.util.List.of(oldest));

        refreshTokenService.createRefreshToken(testUser);

        assertTrue(oldest.isRevoked());
        verify(refreshTokenRepository, atLeastOnce()).save(oldest);
    }
}
