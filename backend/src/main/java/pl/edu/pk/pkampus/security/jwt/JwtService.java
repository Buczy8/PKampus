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
import java.util.function.Function;

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

    public String generateToken(User user, String roomNumber) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("dormitory_id", user.getDormitory() != null ? user.getDormitory().getId().toString() : null);
        claims.put("room_number", roomNumber);

        return buildToken(claims, user.getId().toString(), expirationMinutes, ChronoUnit.MINUTES);
    }

    public String generateToken(User user) {
        return generateToken(user, null);
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

    public UUID extractUserId(String token) {
        String sub = extractClaim(token, Claims::getSubject);
        return sub != null ? UUID.fromString(sub) : null;
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public UUID extractDormitoryId(String token) {
        String dormId = extractClaim(token, claims -> claims.get("dormitory_id", String.class));
        return dormId != null ? UUID.fromString(dormId) : null;
    }

    public String extractRoomNumber(String token) {
        return extractClaim(token, claims -> claims.get("room_number", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return (email.equals(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
