package pl.edu.pk.pkampus.security.token;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.edu.pk.pkampus.common.exception.EmailVerificationTokenInvalidException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class SignedEmailTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${app.security.email-token-ttl-hours:24}")
    private long ttlHours;

    private SecretKeySpec secretKeySpec;

    @PostConstruct
    public void init() {
        this.secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    }

    /**
     * Generates a stateless, cryptographic HMAC-SHA256 signed verification token.
     *
     * @param userId user UUID identifier
     * @param email  user email address
     * @return URL-safe signed token in format: base64Url(payload).base64Url(hmac)
     */
    public String generateToken(UUID userId, String email) {
        Instant expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS);
        String rawPayload = userId.toString() + ":" + expiresAt.toEpochMilli() + ":" + email;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sign(encodedPayload.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        return encodedPayload + "." + encodedSignature;
    }

    /**
     * Verifies the cryptographic HMAC signature and TTL expiration of the given token.
     *
     * @param token URL-safe signed token
     * @return verified EmailTokenPayload containing userId, email, and expiration Instant
     * @throws EmailVerificationTokenInvalidException if token format is invalid, signature is forged, or token has expired
     */
    public EmailTokenPayload verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new EmailVerificationTokenInvalidException("Token cannot be empty");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new EmailVerificationTokenInvalidException("Invalid token format");
        }

        String encodedPayload = parts[0];
        String encodedSignature = parts[1];

        byte[] expectedSignature = sign(encodedPayload.getBytes(StandardCharsets.UTF_8));
        byte[] actualSignature;
        try {
            actualSignature = Base64.getUrlDecoder().decode(encodedSignature);
        } catch (IllegalArgumentException e) {
            throw new EmailVerificationTokenInvalidException("Invalid signature encoding");
        }

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new EmailVerificationTokenInvalidException("Token signature mismatch or tampering detected");
        }

        String rawPayload;
        try {
            rawPayload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new EmailVerificationTokenInvalidException("Invalid payload encoding");
        }

        String[] payloadParts = rawPayload.split(":", 3);
        if (payloadParts.length != 3) {
            throw new EmailVerificationTokenInvalidException("Invalid payload structure");
        }

        try {
            UUID userId = UUID.fromString(payloadParts[0]);
            long expiryMillis = Long.parseLong(payloadParts[1]);
            String email = payloadParts[2];
            Instant expiresAt = Instant.ofEpochMilli(expiryMillis);

            if (Instant.now().isAfter(expiresAt)) {
                throw new EmailVerificationTokenInvalidException("Email verification token has expired");
            }

            return new EmailTokenPayload(userId, email, expiresAt);
        } catch (IllegalArgumentException e) {
            throw new EmailVerificationTokenInvalidException("Malformed payload data: " + e.getMessage());
        }
    }

    private byte[] sign(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    public long getTtlHours() {
        return ttlHours;
    }
}
