package pl.edu.pk.pkampus.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthRateLimiterService {

    @Getter
    @Value("${app.security.rate-limit.login.capacity:5}")
    private long loginCapacity;

    @Getter
    @Value("${app.security.rate-limit.login.duration-minutes:1}")
    private long loginDurationMinutes;

    @Getter
    @Value("${app.security.rate-limit.register.capacity:3}")
    private long registerCapacity;

    @Getter
    @Value("${app.security.rate-limit.register.duration-minutes:10}")
    private long registerDurationMinutes;

    @Getter
    @Value("${app.security.rate-limit.refresh.capacity:30}")
    private long refreshCapacity;

    @Getter
    @Value("${app.security.rate-limit.refresh.duration-minutes:1}")
    private long refreshDurationMinutes;

    @Getter
    @Value("${app.security.rate-limit.forgot-password.capacity:3}")
    private long forgotPasswordCapacity;

    @Getter
    @Value("${app.security.rate-limit.forgot-password.duration-minutes:10}")
    private long forgotPasswordDurationMinutes;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(20_000)
            .build();

    public ConsumptionProbe tryConsume(AuthRateLimitEndpoint endpoint, String ipAddress) {
        String key = endpoint.name() + ":" + ipAddress;
        return buckets.get(key, k -> createBucket(endpoint)).tryConsumeAndReturnRemaining(1);
    }

    private Bucket createBucket(AuthRateLimitEndpoint endpoint) {
        long capacity;
        long durationMinutes;
        switch (endpoint) {
            case REGISTER -> {
                capacity = registerCapacity;
                durationMinutes = registerDurationMinutes;
            }
            case REFRESH -> {
                capacity = refreshCapacity;
                durationMinutes = refreshDurationMinutes;
            }
            case FORGOT_PASSWORD -> {
                capacity = forgotPasswordCapacity;
                durationMinutes = forgotPasswordDurationMinutes;
            }
            case LOGIN -> {
                capacity = loginCapacity;
                durationMinutes = loginDurationMinutes;
            }
            default -> throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(durationMinutes))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
