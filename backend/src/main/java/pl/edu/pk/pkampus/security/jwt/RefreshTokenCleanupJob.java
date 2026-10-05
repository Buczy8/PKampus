package pl.edu.pk.pkampus.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pl.edu.pk.pkampus.modules.auth.passwordreset.PasswordResetTokenRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Daily cleanup at 03:15 — removes revoked tokens older than 1 day and tokens expired for over 7 days.
     */
    @Scheduled(cron = "${app.scheduling.token-cleanup.cron:0 15 3 * * *}")
    @Transactional
    public void cleanupStaleRefreshTokens() {
        Instant revokedBefore = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant expiredBefore = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = refreshTokenRepository.deleteStaleTokens(revokedBefore, expiredBefore);
        if (deleted > 0) {
            log.info("Cleaned up {} stale refresh tokens", deleted);
        } else {
            log.debug("Refresh token cleanup: nothing to delete");
        }

        int deletedResetTokens = passwordResetTokenRepository.deleteStaleTokens(Instant.now().minus(1, ChronoUnit.DAYS));
        if (deletedResetTokens > 0) {
            log.info("Cleaned up {} stale password reset tokens", deletedResetTokens);
        }
    }
}
