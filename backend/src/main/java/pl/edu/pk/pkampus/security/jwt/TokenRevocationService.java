package pl.edu.pk.pkampus.security.jwt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * High-performance in-memory revocation cache using Caffeine (UC-AUTH-02, NFR-SEC-01).
 * When an account is BLOCKED, CHECKED_OUT, or user role is modified, the user's ID
 * is registered in this blacklist with TTL matching the JWT access token lifetime (15 min).
 * Also revokes database-stored refresh tokens for the user.
 */
@Service
public class TokenRevocationService {

    private final Cache<UUID, Boolean> revocationCache;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticatedUserCache authenticatedUserCache;

    public TokenRevocationService(
            @Value("${jwt.expiration-minutes:15}") long expirationMinutes,
            RefreshTokenRepository refreshTokenRepository,
            AuthenticatedUserCache authenticatedUserCache
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticatedUserCache = authenticatedUserCache;
        this.revocationCache = Caffeine.newBuilder()
                .expireAfterWrite(expirationMinutes, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    @Transactional
    public void revokeUser(UUID userId) {
        if (userId != null) {
            blacklistAccessToken(userId);
            refreshTokenRepository.revokeAllByUserId(userId);
        }
    }

    /**
     * Blacklists access JWTs for the user without revoking all refresh tokens.
     */
    public void blacklistAccessToken(UUID userId) {
        if (userId != null) {
            revocationCache.put(userId, Boolean.TRUE);
            authenticatedUserCache.invalidate(userId);
        }
    }

    public boolean isRevoked(UUID userId) {
        if (userId == null) {
            return false;
        }
        return Boolean.TRUE.equals(revocationCache.getIfPresent(userId));
    }

    public void clearRevocation(UUID userId) {
        if (userId != null) {
            revocationCache.invalidate(userId);
        }
    }
}
