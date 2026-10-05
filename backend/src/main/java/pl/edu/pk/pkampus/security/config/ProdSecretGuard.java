package pl.edu.pk.pkampus.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Fail-fast guard against running the {@code prod} profile with the publicly
 * known development secrets. The fallbacks in {@code application.yml} let local
 * development boot without extra setup, but if a production deployment forgets
 * to provide real values anyone holding the repository could forge JWTs and
 * email tokens — refuse to start instead of running compromised.
 */
@Component
@RequiredArgsConstructor
public class ProdSecretGuard {

    // Must mirror the dev-only fallbacks in application.yml (jwt.secret, app.security.email-token-secret).
    static final String DEV_JWT_SECRET =
            "pkampus_jwt_super_secret_key_development_min_32_characters_long_2026!";
    static final String DEV_EMAIL_TOKEN_SECRET =
            "pkampus_email_activation_secret_key_dev_min_32_chars_2026!";

    private final Environment environment;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.security.email-token-secret:}")
    private String emailTokenSecret;

    @PostConstruct
    public void guard() {
        if (!isProdProfile()) {
            return;
        }
        if (jwtSecret == null || jwtSecret.isBlank() || DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Refusing to start with prod profile: jwt.secret must be provided via JWT_SECRET "
                            + "(the development fallback is publicly known)");
        }
        if (emailTokenSecret == null || emailTokenSecret.isBlank()
                || DEV_EMAIL_TOKEN_SECRET.equals(emailTokenSecret)) {
            throw new IllegalStateException(
                    "Refusing to start with prod profile: app.security.email-token-secret must be provided "
                            + "via EMAIL_TOKEN_SECRET (the development fallback is publicly known)");
        }
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
