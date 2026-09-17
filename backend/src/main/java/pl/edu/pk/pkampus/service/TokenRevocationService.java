package pl.edu.pk.pkampus.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * High-performance in-memory revocation cache using Caffeine (UC-AUTH-02, NFR-SEC-01).
 * When an account is BLOCKED, CHECKED_OUT, or user role is modified, the user's ID
 * is registered in this blacklist with TTL matching the JWT access token lifetime (15 min).
 */
@Service
public class TokenRevocationService {

    private final Cache<UUID, Boolean> revocationCache;

    public TokenRevocationService(@Value("${jwt.expiration-minutes:15}") long expirationMinutes) {
        this.revocationCache = Caffeine.newBuilder()
                .expireAfterWrite(expirationMinutes, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    public void revokeUser(UUID userId) {
        if (userId != null) {
            revocationCache.put(userId, Boolean.TRUE);
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
