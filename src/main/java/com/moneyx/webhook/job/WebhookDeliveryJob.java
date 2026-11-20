package com.moneyx.webhook.job;

import com.moneyx.shared.observability.RequestIds;
import com.moneyx.webhook.service.WebhookDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * A cada 5 s, envia as entregas de webhook pendentes. Desligado com moneyx.jobs.enabled=false (testes).
 *
 * fixedDelay: duas execuções nunca se sobrepõem. O agendador tem 2 threads (spring.task.scheduling.pool.size):
 * uma loja lenta atrasa as entregas, mas não o job de expiração de pagamentos.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "moneyx.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryJob {

    private final WebhookDeliveryService deliveries;

    public WebhookDeliveryJob(WebhookDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void deliverDue() {
        RequestIds.runWithNewId("job", deliveries::deliverDue);
    }
}
