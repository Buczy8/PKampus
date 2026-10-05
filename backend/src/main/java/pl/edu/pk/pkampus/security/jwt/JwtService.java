package pl.edu.pk.pkampus.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import pl.edu.pk.pkampus.modules.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-minutes:15}")
    private long expirationMinutes;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());

        return buildToken(claims, user.getId().toString(), expirationMinutes, ChronoUnit.MINUTES);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long amountToAdd, ChronoUnit unit) {
        Instant now = Instant.now();
        Instant expiry = now.plus(amountToAdd, unit);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Minimal verified view of an access token. Parsed exactly once per call —
     * callers must reuse the returned value instead of invoking the individual
     * extractors repeatedly (each of which would re-verify the signature).
     */
    public record AccessTokenClaims(UUID userId, String email, Instant expiresAt) {
    }

    /**
     * Parses and verifies the token signature exactly once and returns the
     * claims required for authentication.
     *
     * @throws JwtException if the signature is invalid or the token is malformed/expired
     * @throws IllegalArgumentException if a required claim is missing
     */
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        Date expiration = claims.getExpiration();
        if (subject == null || email == null || expiration == null) {
            throw new IllegalArgumentException("Token is missing required claims");
        }
        return new AccessTokenClaims(UUID.fromString(subject), email, expiration.toInstant());
    }

    public UUID extractUserId(String token) {
        return parseAccessToken(token).userId();
    }

    public String extractEmail(String token) {
        return parseAccessToken(token).email();
    }

    public Date extractExpiration(String token) {
        return Date.from(parseAccessToken(token).expiresAt());
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            AccessTokenClaims claims = parseAccessToken(token);
            return userDetails.getUsername() != null
                    && userDetails.getUsername().equals(claims.email())
                    && claims.expiresAt().isAfter(Instant.now());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return parseAccessToken(token).expiresAt().isBefore(Instant.now());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
