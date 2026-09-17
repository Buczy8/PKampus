package pl.edu.pk.pkampus.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class LoginRateLimiterService {

    @Getter
    @Value("${app.security.rate-limit.login.capacity:5}")
    private long capacity;

    @Getter
    @Value("${app.security.rate-limit.login.duration-minutes:1}")
    private long durationMinutes;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    public Bucket resolveBucket(String ipAddress) {
        return buckets.get(ipAddress, this::createNewBucket);
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(durationMinutes))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public ConsumptionProbe tryConsume(String ipAddress) {
        return resolveBucket(ipAddress).tryConsumeAndReturnRemaining(1);
    }

    public void reset(String ipAddress) {
        buckets.invalidate(ipAddress);
    }
}
