package pl.edu.pk.pkampus.security.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterServiceTest {

    private LoginRateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new LoginRateLimiterService();
        ReflectionTestUtils.setField(service, "capacity", 5L);
        ReflectionTestUtils.setField(service, "durationMinutes", 1L);
    }

    @Test
    void shouldAllowRequestsUpToCapacity() {
        String ip = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            ConsumptionProbe probe = service.tryConsume(ip);
            assertTrue(probe.isConsumed(), "Request " + (i + 1) + " should be permitted");
            assertEquals(4 - i, probe.getRemainingTokens());
        }

        ConsumptionProbe sixthProbe = service.tryConsume(ip);
        assertFalse(sixthProbe.isConsumed(), "6th request should exceed capacity");
        assertTrue(sixthProbe.getNanosToWaitForRefill() > 0);
    }

    @Test
    void shouldIsolateRateLimitsByIpAddress() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        for (int i = 0; i < 5; i++) {
            service.tryConsume(ip1);
        }
        assertFalse(service.tryConsume(ip1).isConsumed());

        // Different IP should still have full quota
        ConsumptionProbe probe2 = service.tryConsume(ip2);
        assertTrue(probe2.isConsumed());
        assertEquals(4, probe2.getRemainingTokens());
    }

    @Test
    void shouldResetBucketForIp() {
        String ip = "172.16.0.1";

        for (int i = 0; i < 5; i++) {
            service.tryConsume(ip);
        }
        assertFalse(service.tryConsume(ip).isConsumed());

        service.reset(ip);

        ConsumptionProbe probeAfterReset = service.tryConsume(ip);
        assertTrue(probeAfterReset.isConsumed());
        assertEquals(4, probeAfterReset.getRemainingTokens());
    }
}
