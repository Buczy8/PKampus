package pl.edu.pk.pkampus.modules.board;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.edu.pk.pkampus.common.exception.RateLimitExceededException;

import java.time.Duration;
import java.util.UUID;

/**
 * Token-bucket rate limiter per authenticated resident for community board operations.
 * Protects against spamming posts and comments.
 */
@Service
class BoardRateLimiterService {

    @Getter
    @Value("${app.board.rate-limit.post.capacity:5}")
    private long postCapacity = 5;

    @Getter
    @Value("${app.board.rate-limit.post.duration-minutes:10}")
    private long postDurationMinutes = 10;

    @Getter
    @Value("${app.board.rate-limit.comment.capacity:15}")
    private long commentCapacity = 15;

    @Getter
    @Value("${app.board.rate-limit.comment.duration-minutes:1}")
    private long commentDurationMinutes = 1;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(20_000)
            .build();

    public void checkPostRateLimit(UUID userId) {
        String key = "POST:" + userId;
        ConsumptionProbe probe = buckets.get(key, k -> createPostBucket()).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long secondsToWait = (probe.getNanosToWaitForRefill() / 1_000_000_000L) + 1;
            throw new RateLimitExceededException(
                    "You are posting too frequently. Please wait " + secondsToWait + " seconds before publishing another post.",
                    secondsToWait
            );
        }
    }

    public void checkCommentRateLimit(UUID userId) {
        String key = "COMMENT:" + userId;
        ConsumptionProbe probe = buckets.get(key, k -> createCommentBucket()).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long secondsToWait = (probe.getNanosToWaitForRefill() / 1_000_000_000L) + 1;
            throw new RateLimitExceededException(
                    "You are commenting too frequently. Please wait " + secondsToWait + " seconds before adding another comment.",
                    secondsToWait
            );
        }
    }

    public void reset(UUID userId) {
        buckets.invalidate("POST:" + userId);
        buckets.invalidate("COMMENT:" + userId);
    }

    void setPostLimits(long capacity, long durationMinutes) {
        this.postCapacity = capacity;
        this.postDurationMinutes = durationMinutes;
    }

    void setCommentLimits(long capacity, long durationMinutes) {
        this.commentCapacity = capacity;
        this.commentDurationMinutes = durationMinutes;
    }

    private Bucket createPostBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(postCapacity)
                .refillIntervally(postCapacity, Duration.ofMinutes(postDurationMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createCommentBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(commentCapacity)
                .refillIntervally(commentCapacity, Duration.ofMinutes(commentDurationMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
