package pl.edu.pk.pkampus.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days:7}")
    private long refreshExpirationDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateSecureRandomToken();
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Created refresh token for user {}", user.getId());

        return rawToken;
    }

    @Transactional
    public RefreshTokenResult rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AccountStatusException("Invalid refresh token"));

        // If a revoked token is presented, suspect token reuse / compromise and revoke all tokens for this user!
        if (existingToken.isRevoked()) {
            log.warn("Attempted reuse of revoked refresh token! Revoking all sessions for user {}", existingToken.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(existingToken.getUser().getId());
            throw new AccountStatusException("Refresh token has been revoked due to security violation");
        }

        if (existingToken.isExpired()) {
            throw new AccountStatusException("Refresh token has expired");
        }

        User user = existingToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("User account is not active");
        }

        // Revoke the old token
        existingToken.setRevoked(true);

        // Issue new refresh token
        String newRawToken = generateSecureRandomToken();
        String newTokenHash = hashToken(newRawToken);
        Instant expiresAt = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);

        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newTokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        RefreshToken savedNewToken = refreshTokenRepository.save(newToken);
        existingToken.setReplacedByToken(savedNewToken);
        refreshTokenRepository.save(existingToken);

        log.debug("Rotated refresh token for user {}", user.getId());
        return new RefreshTokenResult(newRawToken, user);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.debug("Revoked refresh token for user {}", token.getUser().getId());
        });
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        if (userId != null) {
            refreshTokenRepository.revokeAllByUserId(userId);
        }
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationDays * 24 * 60 * 60;
    }

    private String generateSecureRandomToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public record RefreshTokenResult(String newRawToken, User user) {}
}
