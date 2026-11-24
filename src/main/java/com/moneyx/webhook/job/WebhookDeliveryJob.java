package com.moneyx.webhook.job;

import com.moneyx.shared.observability.RequestIds;
import com.moneyx.webhook.service.WebhookDeliveryRetentionService;
import com.moneyx.webhook.service.WebhookDeliveryService;
import com.moneyx.webhook.service.WebhookSecretRotationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * Jobs das entregas de webhook. Desligados com moneyx.jobs.enabled=false (testes).
 *
 * - A cada 5 s, envia as entregas pendentes.
 * - A cada 1 hora, apaga as entregas finalizadas que passaram do prazo de retenção.
 * - A cada 1 hora (a 1ª, 1 min após subir), recifra com a chave ativa os segredos que ainda usam outra chave.
 *
 * fixedDelay: duas execuções do mesmo job nunca se sobrepõem. O agendador tem 2 threads
 * (spring.task.scheduling.pool.size): uma loja lenta atrasa as entregas, mas não os outros jobs.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "moneyx.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryJob.class);

    private final WebhookDeliveryService deliveries;
    private final WebhookDeliveryRetentionService retention;
    private final WebhookSecretRotationService secretRotation;

    public WebhookDeliveryJob(WebhookDeliveryService deliveries,
                              WebhookDeliveryRetentionService retention,
                              WebhookSecretRotationService secretRotation) {
        this.deliveries = deliveries;
        this.retention = retention;
        this.secretRotation = secretRotation;
    }

    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void deliverDue() {
        RequestIds.runWithNewId("job", deliveries::deliverDue);
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpiredDeliveries() {
        RequestIds.runWithNewId("job", () -> {
            int purged = retention.purgeExpired();
            if (purged > 0) {
                log.info("Purged {} webhook deliver(ies) past the retention period", purged);
            }
            return purged;
        });
    }

    @Scheduled(fixedDelay = 60, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void reencryptSecrets() {
        RequestIds.runWithNewId("job", secretRotation::reencryptAll);
    }
}
