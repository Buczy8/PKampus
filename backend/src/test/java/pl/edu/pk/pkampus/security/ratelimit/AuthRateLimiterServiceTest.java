package pl.edu.pk.pkampus.security.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterServiceTest {

    private AuthRateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new AuthRateLimiterService();
        ReflectionTestUtils.setField(service, "loginCapacity", 5L);
        ReflectionTestUtils.setField(service, "loginDurationMinutes", 1L);
        ReflectionTestUtils.setField(service, "registerCapacity", 3L);
        ReflectionTestUtils.setField(service, "registerDurationMinutes", 10L);
        ReflectionTestUtils.setField(service, "refreshCapacity", 30L);
        ReflectionTestUtils.setField(service, "refreshDurationMinutes", 1L);
    }

    @Test
    void shouldAllowLoginRequestsUpToCapacity() {
        // Arrange
        String ip = "192.168.1.100";

        // Act & Assert
        for (int i = 0; i < 5; i++) {
            ConsumptionProbe probe = service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip);
            assertTrue(probe.isConsumed(), "Request " + (i + 1) + " should be permitted");
            assertEquals(4 - i, probe.getRemainingTokens());
        }

        ConsumptionProbe sixthProbe = service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip);
        assertFalse(sixthProbe.isConsumed(), "6th request should exceed capacity");
        assertTrue(sixthProbe.getNanosToWaitForRefill() > 0);
    }

    @Test
    void shouldIsolateRateLimitsByEndpointAndIp() {
        // Arrange
        String ip = "10.0.0.1";

        // Act
        for (int i = 0; i < 5; i++) {
            service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip);
        }

        // Assert
        assertFalse(service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip).isConsumed());

        ConsumptionProbe registerProbe = service.tryConsume(AuthRateLimitEndpoint.REGISTER, ip);
        assertTrue(registerProbe.isConsumed());
        assertEquals(2, registerProbe.getRemainingTokens());
    }

    @Test
    void shouldApplyRegisterCapacityOfThree() {
        // Arrange
        String ip = "172.16.0.1";

        // Act & Assert
        for (int i = 0; i < 3; i++) {
            assertTrue(service.tryConsume(AuthRateLimitEndpoint.REGISTER, ip).isConsumed());
        }
        assertFalse(service.tryConsume(AuthRateLimitEndpoint.REGISTER, ip).isConsumed());
    }

    @Test
    void shouldApplyRefreshCapacityOfThirty() {
        // Arrange
        String ip = "172.16.0.2";

        // Act & Assert
        for (int i = 0; i < 30; i++) {
            assertTrue(service.tryConsume(AuthRateLimitEndpoint.REFRESH, ip).isConsumed());
        }
        assertFalse(service.tryConsume(AuthRateLimitEndpoint.REFRESH, ip).isConsumed());
    }

    @Test
    void shouldResetBucketForEndpointAndIp() {
        // Arrange
        String ip = "172.16.0.1";

        for (int i = 0; i < 5; i++) {
            service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip);
        }
        assertFalse(service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip).isConsumed());

        // Act
        service.reset(AuthRateLimitEndpoint.LOGIN, ip);

        // Assert
        ConsumptionProbe probeAfterReset = service.tryConsume(AuthRateLimitEndpoint.LOGIN, ip);
        assertTrue(probeAfterReset.isConsumed());
        assertEquals(4, probeAfterReset.getRemainingTokens());
    }

    @Test
    void shouldReturnConfiguredPropertiesViaGetters() {
        // Arrange & Act & Assert
        assertEquals(5L, service.getLoginCapacity());
        assertEquals(1L, service.getLoginDurationMinutes());
        assertEquals(3L, service.getRegisterCapacity());
        assertEquals(10L, service.getRegisterDurationMinutes());
        assertEquals(30L, service.getRefreshCapacity());
        assertEquals(1L, service.getRefreshDurationMinutes());
    }
}
