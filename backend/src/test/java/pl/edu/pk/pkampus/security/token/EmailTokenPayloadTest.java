package pl.edu.pk.pkampus.security.token;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmailTokenPayloadTest {

    @Test
    void shouldCreatePayloadAndExposeProperties() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "student@pk.edu.pl";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // Act
        EmailTokenPayload payload = new EmailTokenPayload(userId, email, expiresAt);

        // Assert
        assertEquals(userId, payload.userId());
        assertEquals(email, payload.email());
        assertEquals(expiresAt, payload.expiresAt());
    }

    @Test
    void shouldVerifyRecordEqualityAndHashCode() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "student@pk.edu.pl";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        EmailTokenPayload p1 = new EmailTokenPayload(userId, email, expiresAt);
        EmailTokenPayload p2 = new EmailTokenPayload(userId, email, expiresAt);
        EmailTokenPayload p3 = new EmailTokenPayload(UUID.randomUUID(), email, expiresAt);

        // Act & Assert
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
        assertTrue(p1.toString().contains("student@pk.edu.pl"));
    }
}
