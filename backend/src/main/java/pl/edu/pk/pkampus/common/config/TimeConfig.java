package pl.edu.pk.pkampus.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    /**
     * Display zone for timestamps presented to users (Europe/Warsaw).
     * Use this constant instead of duplicating {@code ZoneId.of("Europe/Warsaw")}.
     */
    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
