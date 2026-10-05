package pl.edu.pk.pkampus.security.jwt;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @EntityGraph(attributePaths = {"user", "user.dormitory"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Locking lookup for rotation: the revoked-check-then-revoke sequence in
     * {@link RefreshTokenService#rotateRefreshToken} must be atomic, otherwise
     * two concurrent refreshes with the same token both succeed and mint two
     * successor tokens (replay window).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "user.dormitory"})
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);

    long countByUser_IdAndRevokedFalseAndExpiresAtAfter(UUID userId, Instant now);

    List<RefreshToken> findByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
            UUID userId,
            Instant now
    );

    @Modifying
    @Query("""
            DELETE FROM RefreshToken rt
            WHERE (rt.revoked = true AND rt.createdAt < :revokedBefore)
               OR (rt.expiresAt < :expiredBefore)
            """)
    int deleteStaleTokens(
            @Param("revokedBefore") Instant revokedBefore,
            @Param("expiredBefore") Instant expiredBefore
    );
}
