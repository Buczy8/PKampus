package pl.edu.pk.pkampus.security.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.edu.pk.pkampus.common.exception.EmailVerificationTokenInvalidException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SignedEmailTokenService Spring Integration Tests")
class SignedEmailTokenServiceIntegrationTest {

    @Autowired
    private SignedEmailTokenService tokenService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("Should successfully load SignedEmailTokenService bean with test configuration and perform token roundtrip")
    void shouldGenerateAndVerifyTokenInSpringContext() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "resident.it@pk.edu.pl";

        // Act
        String token = tokenService.generateToken(userId, email);
        EmailTokenPayload payload = tokenService.verifyToken(token);

        // Assert
        assertNotNull(token);
        assertEquals(userId, payload.userId());
        assertEquals(email, payload.email());
        assertTrue(payload.expiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Should reject forged signature in Spring context")
    void shouldRejectForgedTokenInSpringContext() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String email = "resident.it@pk.edu.pl";
        String token = tokenService.generateToken(userId, email);
        String forgedToken = token + "forged";

        // Act & Assert
        assertThrows(EmailVerificationTokenInvalidException.class, () -> tokenService.verifyToken(forgedToken));
    }
}
