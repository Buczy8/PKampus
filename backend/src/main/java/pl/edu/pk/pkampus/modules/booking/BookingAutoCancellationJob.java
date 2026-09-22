package pl.edu.pk.pkampus.modules.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingAutoCancellationJob {

    private final BookingAutoCancellationService autoCancellationService;

    @Value("${app.scheduling.auto-cancellation.enabled:true}")
    private boolean enabled;

    @Value("${app.scheduling.auto-cancellation.late-threshold-minutes:15}")
    private int thresholdMinutes;

    /**
     * Executes every minute (cron = "0 * * * * *") to enforce the 15-minute rule (BR-02 / NFR-REL-03).
     * Releases unclaimed laundry and room reservations whose start time is over 15 minutes in the past.
     */
    @Scheduled(cron = "${app.scheduling.auto-cancellation.cron:0 * * * * *}")
    public void runAutoCancellation() {
        if (!enabled) {
            log.trace("Booking auto-cancellation is disabled via configuration");
            return;
        }
        try {
            autoCancellationService.cancelAllExpiredBookings(thresholdMinutes);
        } catch (Exception e) {
            log.error("Failed executing scheduled booking auto-cancellation job", e);
        }
    }
}
