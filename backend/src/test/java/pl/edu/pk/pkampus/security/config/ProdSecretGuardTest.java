package pl.edu.pk.pkampus.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdSecretGuardTest {

    @Mock
    private Environment environment;

    private ProdSecretGuard guard(String jwtSecret, String emailTokenSecret) {
        ProdSecretGuard guard = new ProdSecretGuard(environment);
        ReflectionTestUtils.setField(guard, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(guard, "emailTokenSecret", emailTokenSecret);
        return guard;
    }

    @Test
    void shouldRejectDevJwtSecretOnProdProfile() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                guard(ProdSecretGuard.DEV_JWT_SECRET, "custom-email-secret-12345678901234567890").guard());
        assertTrue(ex.getMessage().contains("jwt.secret"));
    }

    @Test
    void shouldRejectDevEmailTokenSecretOnProdProfile() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                guard("custom-jwt-secret-12345678901234567890123456", ProdSecretGuard.DEV_EMAIL_TOKEN_SECRET).guard());
        assertTrue(ex.getMessage().contains("email-token-secret"));
    }

    @Test
    void shouldAllowCustomSecretsOnProdProfile() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        // Act & Assert
        assertDoesNotThrow(() -> guard(
                "custom-jwt-secret-12345678901234567890123456",
                "custom-email-secret-12345678901234567890").guard());
    }

    @Test
    void shouldAllowDevSecretsOnNonProdProfile() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        // Act & Assert
        assertDoesNotThrow(() -> guard(
                ProdSecretGuard.DEV_JWT_SECRET, ProdSecretGuard.DEV_EMAIL_TOKEN_SECRET).guard());
    }
}
