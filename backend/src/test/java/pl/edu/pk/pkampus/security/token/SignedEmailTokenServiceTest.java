package pl.edu.pk.pkampus.security.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.edu.pk.pkampus.common.exception.EmailVerificationTokenInvalidException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SignedEmailTokenServiceTest {

    private SignedEmailTokenService tokenService;
    private final UUID testUserId = UUID.randomUUID();
    private final String testEmail = "student@pk.edu.pl";

    @BeforeEach
    void setUp() {
        tokenService = new SignedEmailTokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "super_secret_signing_key_for_testing_purposes_123");
        ReflectionTestUtils.setField(tokenService, "ttlHours", 24L);
        tokenService.init();
    }

    @Test
    void shouldGenerateAndVerifyValidToken() {
        // Arrange
        String token = tokenService.generateToken(testUserId, testEmail);

        // Act
        EmailTokenPayload payload = tokenService.verifyToken(token);

        // Assert
        assertNotNull(token);
        assertTrue(token.contains("."));
        assertEquals(testUserId, payload.userId());
        assertEquals(testEmail, payload.email());
        assertTrue(payload.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void shouldRejectTamperedTokenSignature() {
        // Arrange
        String token = tokenService.generateToken(testUserId, testEmail);
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "tampered";

        // Act & Assert
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(tamperedToken));
    }

    @Test
    void shouldRejectTamperedTokenPayload() {
        // Arrange
        String token = tokenService.generateToken(testUserId, testEmail);
        String[] parts = token.split("\\.");

        String modifiedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((UUID.randomUUID() + ":" + (System.currentTimeMillis() + 100000) + ":" + "hacker@pk.edu.pl").getBytes());
        String tamperedToken = modifiedPayload + "." + parts[1];

        // Act & Assert
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(tamperedToken));
    }

    @Test
    void shouldRejectExpiredToken() {
        // Arrange
        ReflectionTestUtils.setField(tokenService, "ttlHours", -1L);
        String expiredToken = tokenService.generateToken(testUserId, testEmail);

        // Act & Assert
        EmailVerificationTokenInvalidException ex = assertThrows(
                EmailVerificationTokenInvalidException.class,
                () -> tokenService.verifyToken(expiredToken)
        );
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void shouldRejectMalformedToken() {
        // Arrange & Act & Assert
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken("not-a-valid-token"));
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(null));
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(""));
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken("one.two.three"));
    }

    @Test
    void shouldRejectTokenWithInvalidSignatureEncoding() {
        // Arrange
        String token = tokenService.generateToken(testUserId, testEmail);
        String[] parts = token.split("\\.");
        String invalidSignatureEncoding = parts[0] + ".!!!invalid-base64!!!";

        // Act & Assert
        EmailVerificationTokenInvalidException ex = assertThrows(
                EmailVerificationTokenInvalidException.class,
                () -> tokenService.verifyToken(invalidSignatureEncoding)
        );
        assertTrue(ex.getMessage().contains("Invalid signature encoding"));
    }

    @Test
    void shouldRejectTokenWithInvalidPayloadStructure() {
        // Arrange
        // Create valid signature for a payload with only 2 segments instead of 3
        String malformedRawPayload = testUserId.toString() + ":" + System.currentTimeMillis();
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(malformedRawPayload.getBytes(StandardCharsets.UTF_8));
        String validTokenForMalformedPayload = tokenService.generateToken(testUserId, testEmail);
        String[] validParts = validTokenForMalformedPayload.split("\\.");

        // Act & Assert (tampering detected or invalid structure)
        assertThrows(EmailVerificationTokenInvalidException.class,
                () -> tokenService.verifyToken(encodedPayload + "." + validParts[1]));
    }

    @Test
    void shouldReturnConfiguredTtlHours() {
        // Arrange & Act
        long ttl = tokenService.getTtlHours();

        // Assert
        assertEquals(24L, ttl);
    }
}
