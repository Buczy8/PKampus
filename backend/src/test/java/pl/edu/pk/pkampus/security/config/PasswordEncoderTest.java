package pl.edu.pk.pkampus.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderTest {

    @Test
    void shouldGenerateBCryptHashWithStrength12() {
        SecurityConfig config = new SecurityConfig(null, null, null);
        PasswordEncoder encoder = config.passwordEncoder();

        String rawPassword = "SecurePassword123!";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        // BCrypt with 12 rounds produces hash starting with $2a$12$ or $2b$12$
        assertTrue(encoded.startsWith("$2a$12$") || encoded.startsWith("$2b$12$"),
                "Hash should have cost factor 12 per NFR-SEC-02, but was: " + encoded);
        assertTrue(encoder.matches(rawPassword, encoded));
    }
}
