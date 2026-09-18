package pl.edu.pk.pkampus.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.InvalidTokenException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days:7}")
    private long refreshExpirationDays;

    @Value("${app.security.max-active-refresh-tokens:5}")
    private int maxActiveRefreshTokens;

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
        enforceMaxActiveSessions(user.getId());
        log.debug("Created refresh token for user {}", user.getId());

        return rawToken;
    }

    @Transactional
    public RefreshTokenResult rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (existingToken.isRevoked()) {
            // Do not revoke sibling sessions — concurrent tab refresh races are common in SPAs
            // and "reuse = theft" wipeouts cause false logouts. Reject this token only.
            log.warn("Rejected revoked refresh token for user {} (no global revoke)",
                    existingToken.getUser().getId());
            throw new InvalidTokenException("Refresh token is no longer valid");
        }

        if (existingToken.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        User user = existingToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountStatusException("User account is not active");
        }

        existingToken.setRevoked(true);

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

        enforceMaxActiveSessions(user.getId());
        log.debug("Rotated refresh token for user {}", user.getId());
        return new RefreshTokenResult(newRawToken, user);
    }

    /**
     * Revokes a single refresh token and returns the owning user id when found.
     */
    @Transactional
    public Optional<UUID> revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(rawToken);
        return refreshTokenRepository.findByTokenHash(tokenHash).map(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.debug("Revoked refresh token for user {}", token.getUser().getId());
            return token.getUser().getId();
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

    void enforceMaxActiveSessions(UUID userId) {
        if (userId == null || maxActiveRefreshTokens <= 0) {
            return;
        }
        Instant now = Instant.now();
        long activeCount = refreshTokenRepository.countByUser_IdAndRevokedFalseAndExpiresAtAfter(userId, now);
        if (activeCount <= maxActiveRefreshTokens) {
            return;
        }

        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(userId, now);

        long toRevoke = activeCount - maxActiveRefreshTokens;
        for (int i = 0; i < toRevoke && i < activeTokens.size(); i++) {
            RefreshToken token = activeTokens.get(i);
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked oldest refresh token {} for user {} (session cap {})",
                    token.getId(), userId, maxActiveRefreshTokens);
        }
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
