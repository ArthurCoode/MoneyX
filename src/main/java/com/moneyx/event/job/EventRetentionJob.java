package com.moneyx.event.job;

import com.moneyx.event.service.EventRetentionService;
import com.moneyx.shared.observability.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * A cada 1 hora, apaga os eventos que passaram do prazo de retenção. Desligado com moneyx.jobs.enabled=false (testes).
 *
 * Independe do job que apaga as entregas de webhook: se este rodar antes, os eventos ainda referenciados ficam para
 * a próxima execução. Os dois se acertam sozinhos, sem precisar de uma ordem.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "moneyx.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class EventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(EventRetentionJob.class);

    private final EventRetentionService retention;

    public EventRetentionJob(EventRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpiredEvents() {
        RequestIds.runWithNewId("job", () -> {
            int purged = retention.purgeExpired();
            if (purged > 0) {
                log.info("Purged {} event(s) past the retention period", purged);
            }
            return purged;
        });
    }
}
