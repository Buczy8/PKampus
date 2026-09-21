package pl.edu.pk.pkampus.modules.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardVerificationService unit tests")
class CardVerificationServiceTest {

    private static final String SECRET = "test_jwt_secret_key_minimum_32_characters_long_2026!";

    @Test
    @DisplayName("HMAC derivation is deterministic for a fixed date")
    void deterministicForFixedDate() {
        CardVerificationService service = new CardVerificationService(
                SECRET,
                Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC)
        );

        CardDayToken a = service.tokenForDate(LocalDate.of(2026, 3, 15));
        CardDayToken b = service.tokenForDate(LocalDate.of(2026, 3, 15));

        assertThat(a.dayCode()).isEqualTo(b.dayCode()).hasSize(6);
        assertThat(a.dayColorHex()).isEqualTo(b.dayColorHex()).startsWith("#");
        assertThat(a.dayColorName()).isEqualTo(b.dayColorName()).isNotBlank();
        assertThat(a.validDate()).isEqualTo("2026-03-15");
    }

    @Test
    @DisplayName("Different calendar dates produce different codes")
    void differentDatesDiffer() {
        CardVerificationService service = new CardVerificationService(
                SECRET,
                Clock.systemUTC()
        );

        CardDayToken d1 = service.tokenForDate(LocalDate.of(2026, 3, 15));
        CardDayToken d2 = service.tokenForDate(LocalDate.of(2026, 3, 16));

        assertThat(d1.dayCode()).isNotEqualTo(d2.dayCode());
    }
}
