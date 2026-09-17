package pl.edu.pk.pkampus.security.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.edu.pk.pkampus.common.exception.EmailVerificationTokenInvalidException;

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
        String token = tokenService.generateToken(testUserId, testEmail);

        assertNotNull(token);
        assertTrue(token.contains("."));

        EmailTokenPayload payload = tokenService.verifyToken(token);
        assertEquals(testUserId, payload.userId());
        assertEquals(testEmail, payload.email());
        assertTrue(payload.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void shouldRejectTamperedTokenSignature() {
        String token = tokenService.generateToken(testUserId, testEmail);
        String[] parts = token.split("\\.");

        String tamperedToken = parts[0] + "." + parts[1] + "tampered";

        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(tamperedToken));
    }

    @Test
    void shouldRejectTamperedTokenPayload() {
        String token = tokenService.generateToken(testUserId, testEmail);
        String[] parts = token.split("\\.");

        String modifiedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((UUID.randomUUID() + ":" + (System.currentTimeMillis() + 100000) + ":" + "hacker@pk.edu.pl").getBytes());
        String tamperedToken = modifiedPayload + "." + parts[1];

        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(tamperedToken));
    }

    @Test
    void shouldRejectExpiredToken() {
        ReflectionTestUtils.setField(tokenService, "ttlHours", -1L);

        String expiredToken = tokenService.generateToken(testUserId, testEmail);

        EmailVerificationTokenInvalidException ex = assertThrows(
                EmailVerificationTokenInvalidException.class,
                () -> tokenService.verifyToken(expiredToken)
        );
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken("not-a-valid-token"));
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(null));
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(""));
    }
}
