package com.moneyx.payment.job;

import com.moneyx.payment.service.PaymentMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * Jobs agendados do módulo de pagamentos. Só disparam o serviço; as regras ficam em PaymentMaintenanceService.
 *
 * Ligados por padrão. moneyx.jobs.enabled=false desliga a classe inteira (inclusive o @EnableScheduling):
 * nos testes, um job rodando sozinho mudaria dados no meio dos cenários, então eles chamam o serviço diretamente.
 *
 * fixedDelay: o intervalo conta a partir do FIM da execução anterior, então duas execuções do mesmo job
 * nunca se sobrepõem (com fixedRate, uma execução lenta poderia encavalar na seguinte).
 *
 * Se uma execução lançar exceção, o Spring registra o erro no log e o agendamento continua.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "moneyx.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentMaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(PaymentMaintenanceJobs.class);

    private final PaymentMaintenanceService maintenance;

    public PaymentMaintenanceJobs(PaymentMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    /** A cada 1 minuto: PENDING vencidos -> EXPIRED. */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void expireOverduePayments() {
        int expired = maintenance.expireOverduePayments();
        if (expired > 0) {
            log.info("Expired {} overdue payment(s)", expired);
        }
    }

    /** A cada 1 hora: apaga chaves de idempotência vencidas. */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpiredIdempotencyKeys() {
        int purged = maintenance.purgeExpiredIdempotencyKeys();
        if (purged > 0) {
            log.info("Purged {} expired idempotency key(s)", purged);
        }
    }
}
