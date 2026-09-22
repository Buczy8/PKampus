package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    @Test
    void shouldInitializeWithDefaultValues() {
        // Arrange & Act
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash123")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        // Assert
        assertNotNull(token.getCreatedAt());
        assertFalse(token.isRevoked());
        assertNull(token.getId());
        assertNull(token.getUser());
        assertNull(token.getReplacedByToken());
    }

    @Test
    void shouldReportExpiredWhenExpiresAtIsInPast() {
        // Arrange
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash123")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        // Act & Assert
        assertTrue(token.isExpired());
        assertFalse(token.isValid());
    }

    @Test
    void shouldReportNotExpiredWhenExpiresAtIsInFuture() {
        // Arrange
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash123")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        // Act & Assert
        assertFalse(token.isExpired());
        assertTrue(token.isValid());
    }

    @Test
    void shouldReportInvalidWhenRevokedEvenIfUnexpired() {
        // Arrange
        RefreshToken token = RefreshToken.builder()
                .tokenHash("hash123")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        // Act & Assert
        assertFalse(token.isExpired());
        assertTrue(token.isRevoked());
        assertFalse(token.isValid());
    }

    @Test
    void shouldSupportReplacedByTokenChain() {
        // Arrange
        RefreshToken oldToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("old-hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        RefreshToken newToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("new-hash")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        // Act
        oldToken.setReplacedByToken(newToken);

        // Assert
        assertNotNull(oldToken.getReplacedByToken());
        assertEquals(newToken.getId(), oldToken.getReplacedByToken().getId());
    }
}
