package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.InvalidTokenException;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
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
        // Arrange
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.countByUser_IdAndRevokedFalseAndExpiresAtAfter(eq(testUser.getId()), any()))
                .thenReturn(1L);

        // Act
        String rawToken = refreshTokenService.createRefreshToken(testUser);

        // Assert
        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void shouldRotateRefreshTokenSuccessfully() {
        // Arrange
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

        // Act
        RefreshTokenService.RefreshTokenResult result = refreshTokenService.rotateRefreshToken(rawToken);

        // Assert
        assertNotNull(result);
        assertNotNull(result.newRawToken());
        assertEquals(testUser, result.user());
        assertTrue(existingToken.isRevoked());
        assertNotNull(existingToken.getReplacedByToken());
    }

    @Test
    void shouldRejectRotateWhenTokenNotFound() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidTokenException.class, () -> refreshTokenService.rotateRefreshToken("unknown-token"));
    }

    @Test
    void shouldRejectRevokedTokenWithoutRevokingOtherSessions() {
        // Arrange
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        // Act & Assert
        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotateRefreshToken("reused-token"));

        assertTrue(ex.getMessage().contains("no longer valid"));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void shouldFailWhenTokenIsExpired() {
        // Arrange
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        // Act & Assert
        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotateRefreshToken("expired-token"));

        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void shouldRejectRotateWhenUserStatusIsNotActiveOrMustChangePassword() {
        // Arrange
        testUser.setStatus(UserStatus.BLOCKED);
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        // Act & Assert
        assertThrows(AccountStatusException.class, () -> refreshTokenService.rotateRefreshToken("valid-raw-token"));
    }

    @Test
    void shouldRevokeRefreshToken() {
        // Arrange
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        // Act
        Optional<UUID> ownerId = refreshTokenService.revokeRefreshToken("raw-token");

        // Assert
        assertTrue(token.isRevoked());
        assertTrue(ownerId.isPresent());
        assertEquals(testUser.getId(), ownerId.get());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldReturnEmptyWhenRevokingNullOrBlankToken() {
        // Arrange & Act & Assert
        assertTrue(refreshTokenService.revokeRefreshToken(null).isEmpty());
        assertTrue(refreshTokenService.revokeRefreshToken("   ").isEmpty());
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void shouldReturnEmptyWhenRevokingNonExistentToken() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act
        Optional<UUID> ownerId = refreshTokenService.revokeRefreshToken("nonexistent-token");

        // Assert
        assertTrue(ownerId.isEmpty());
    }

    @Test
    void shouldRevokeAllUserTokens() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        refreshTokenService.revokeAllUserTokens(userId);

        // Assert
        verify(refreshTokenRepository).revokeAllByUserId(userId);
    }

    @Test
    void shouldIgnoreRevokeAllUserTokensWhenUserIdIsNull() {
        // Arrange & Act
        refreshTokenService.revokeAllUserTokens(null);

        // Assert
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void shouldReturnCorrectRefreshExpirationSeconds() {
        // Arrange & Act
        long seconds = refreshTokenService.getRefreshExpirationSeconds();

        // Assert: 7 days * 24 * 60 * 60 = 604800
        assertEquals(604800L, seconds);
    }

    @Test
    void shouldEnforceMaxActiveSessionsByRevokingOldest() {
        // Arrange
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
                .thenReturn(List.of(oldest));

        // Act
        refreshTokenService.createRefreshToken(testUser);

        // Assert
        assertTrue(oldest.isRevoked());
        verify(refreshTokenRepository, atLeastOnce()).save(oldest);
    }
}
