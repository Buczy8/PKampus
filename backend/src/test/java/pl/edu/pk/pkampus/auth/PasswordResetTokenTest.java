package pl.edu.pk.pkampus.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.auth.PasswordResetToken;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordResetToken entity unit tests (AAA)")
class PasswordResetTokenTest {

    @Test
    @DisplayName("Builder should construct entity with valid fields")
    void buildEntity() {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
        Instant now = Instant.now();
        Instant expires = now.plus(15, ChronoUnit.MINUTES);
        String tokenHash = "abc123hash";

        // Act
        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expires)
                .createdAt(now)
                .build();

        // Assert
        assertNotNull(token.getId());
        assertEquals(user, token.getUser());
        assertEquals("abc123hash", token.getTokenHash());
        assertEquals(expires, token.getExpiresAt());
        assertEquals(now, token.getCreatedAt());
        assertNull(token.getUsedAt());
    }

    @Test
    @DisplayName("setUsedAt should update used timestamp")
    void updateUsedAt() {
        // Arrange
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash("hash123")
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        Instant usedTime = Instant.now();

        // Act
        token.setUsedAt(usedTime);

        // Assert
        assertEquals(usedTime, token.getUsedAt());
    }
}
