package pl.edu.pk.pkampus.modules.laundry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Laundry slot grid generation")
class LaundrySlotGridTest {

    @Test
    @DisplayName("3h slots with 07:00–23:00 stop before midnight wrap (no infinite loop)")
    void threeHourSlotsDoNotWrapForever() {
        List<LocalTime> starts = LaundryBookingValidator.slotStarts(
                LocalTime.of(7, 0),
                LocalTime.of(23, 0),
                180
        );

        assertThat(starts).containsExactly(
                LocalTime.of(7, 0),
                LocalTime.of(10, 0),
                LocalTime.of(13, 0),
                LocalTime.of(16, 0),
                LocalTime.of(19, 0)
        );
    }

    @Test
    @DisplayName("90-minute slots keep previous grid behaviour")
    void ninetyMinuteSlots() {
        List<LocalTime> starts = LaundryBookingValidator.slotStarts(
                LocalTime.of(7, 0),
                LocalTime.of(23, 0),
                90
        );

        assertThat(starts).hasSize(10);
        assertThat(starts.getFirst()).isEqualTo(LocalTime.of(7, 0));
        assertThat(starts.getLast()).isEqualTo(LocalTime.of(20, 30));
    }
}
