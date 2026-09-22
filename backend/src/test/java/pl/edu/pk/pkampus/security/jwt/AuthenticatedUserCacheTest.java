package pl.edu.pk.pkampus.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedUserCacheTest {

    private AuthenticatedUserCache cache;
    private User testUser;
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cache = new AuthenticatedUserCache();
        testUser = User.builder()
                .id(testUserId)
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldReturnEmptyWhenUserNotInCache() {
        // Arrange & Act
        Optional<User> result = cache.get(testUserId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenUserIdIsNull() {
        // Arrange & Act
        Optional<User> result = cache.get(null);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPutAndRetrieveUserSuccessfully() {
        // Arrange
        cache.put(testUser);

        // Act
        Optional<User> result = cache.get(testUserId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("student@pk.edu.pl", result.get().getEmail());
    }

    @Test
    void shouldIgnorePutWhenUserOrIdIsNull() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> cache.put(null));
        assertDoesNotThrow(() -> cache.put(User.builder().id(null).build()));
        assertTrue(cache.get(testUserId).isEmpty());
    }

    @Test
    void shouldLoadUserViaLoaderWhenAbsentAndCacheIt() {
        // Arrange
        AtomicInteger loaderCalls = new AtomicInteger(0);

        // Act - First call: triggers loader
        User firstCallResult = cache.getOrLoad(testUserId, id -> {
            loaderCalls.incrementAndGet();
            return testUser;
        });

        // Act - Second call: should serve from cache
        User secondCallResult = cache.getOrLoad(testUserId, id -> {
            loaderCalls.incrementAndGet();
            return testUser;
        });

        // Assert
        assertNotNull(firstCallResult);
        assertNotNull(secondCallResult);
        assertEquals(testUserId, firstCallResult.getId());
        assertEquals(testUserId, secondCallResult.getId());
        assertEquals(1, loaderCalls.get(), "Loader should only be called once; second call should be served from cache");
    }

    @Test
    void shouldReturnNullWhenUserIdIsNullInGetOrLoad() {
        // Arrange
        AtomicInteger loaderCalls = new AtomicInteger(0);

        // Act
        User result = cache.getOrLoad(null, id -> {
            loaderCalls.incrementAndGet();
            return testUser;
        });

        // Assert
        assertNull(result);
        assertEquals(0, loaderCalls.get());
    }

    @Test
    void shouldNotCacheWhenLoaderReturnsNull() {
        // Arrange & Act
        User result = cache.getOrLoad(testUserId, id -> null);

        // Assert
        assertNull(result);
        assertTrue(cache.get(testUserId).isEmpty());
    }

    @Test
    void shouldInvalidateCachedUser() {
        // Arrange
        cache.put(testUser);
        assertTrue(cache.get(testUserId).isPresent());

        // Act
        cache.invalidate(testUserId);

        // Assert
        assertTrue(cache.get(testUserId).isEmpty());
    }

    @Test
    void shouldHandleInvalidateNullGracefully() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> cache.invalidate(null));
    }
}
