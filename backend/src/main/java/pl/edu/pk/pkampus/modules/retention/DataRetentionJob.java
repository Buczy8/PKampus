package pl.edu.pk.pkampus.modules.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final DataRetentionService dataRetentionService;

    @Value("${app.scheduling.retention.enabled:true}")
    private boolean enabled;

    /**
     * Executes nightly (default cron: 0 30 3 * * * = 03:30 AM) to perform RODO data retention procedures.
     */
    @Scheduled(cron = "${app.scheduling.retention.cron:0 30 3 * * *}")
    public void runScheduledRetention() {
        if (!enabled) {
            log.trace("GDPR / RODO data retention job is disabled via configuration");
            return;
        }
        try {
            dataRetentionService.runRetentionTasks();
        } catch (Exception e) {
            log.error("Failed executing scheduled GDPR / RODO data retention job", e);
        }
    }
}
