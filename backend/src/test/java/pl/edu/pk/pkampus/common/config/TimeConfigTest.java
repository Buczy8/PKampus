package pl.edu.pk.pkampus.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("TimeConfig unit tests")
class TimeConfigTest {

    @Test
    @DisplayName("clock() should return UTC system clock")
    void clockShouldBeUtc() {
        TimeConfig timeConfig = new TimeConfig();
        Clock clock = timeConfig.clock();

        assertNotNull(clock);
        assertEquals(ZoneOffset.UTC, clock.getZone());
    }
}
