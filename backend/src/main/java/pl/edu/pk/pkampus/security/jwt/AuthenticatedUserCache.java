package pl.edu.pk.pkampus.security.jwt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import pl.edu.pk.pkampus.modules.user.User;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Short-lived cache of authenticated users keyed by user id to avoid a DB hit on every JWT request.
 */
@Service
public class AuthenticatedUserCache {

    private final Cache<UUID, User> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(45))
            .maximumSize(10_000)
            .build();

    public Optional<User> get(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(userId));
    }

    public User getOrLoad(UUID userId, Function<UUID, User> loader) {
        if (userId == null) {
            return null;
        }
        User cached = cache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        User loaded = loader.apply(userId);
        if (loaded != null) {
            cache.put(userId, loaded);
        }
        return loaded;
    }

    public void put(User user) {
        if (user != null && user.getId() != null) {
            cache.put(user.getId(), user);
        }
    }

    public void invalidate(UUID userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }
}
