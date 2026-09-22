package pl.edu.pk.pkampus.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("""
            SELECT t FROM PasswordResetToken t
            JOIN FETCH t.user
            WHERE t.tokenHash = :tokenHash
              AND t.usedAt IS NULL
            """)
    Optional<PasswordResetToken> findActiveByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.user.id = :userId
              AND t.usedAt IS NULL
            """)
    int invalidateAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken t
            WHERE t.usedAt IS NOT NULL
               OR t.expiresAt < :cutoff
            """)
    int deleteStaleTokens(@Param("cutoff") Instant cutoff);
}
