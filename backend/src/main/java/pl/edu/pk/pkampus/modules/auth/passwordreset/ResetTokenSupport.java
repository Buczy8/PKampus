package pl.edu.pk.pkampus.modules.auth.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Token cryptography helpers for the password-reset flow
 * (generation, hashing, email masking).
 */
public final class ResetTokenSupport {

    public static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private ResetTokenSupport() {
    }

    /** Generates a 32-byte secure random token (64 hex characters). */
    public static String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
