package com.moneyx.payment.job;

import com.moneyx.payment.service.PaymentMaintenanceService;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.shared.observability.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
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

    /** Só retoma criações paradas há mais que isso (a requisição original pode ainda estar em andamento). */
    static final Duration STUCK_CREATION_GRACE = Duration.ofSeconds(30);
    static final int STUCK_CREATION_BATCH = 20;

    private final PaymentMaintenanceService maintenance;
    private final PaymentService payments;

    public PaymentMaintenanceJobs(PaymentMaintenanceService maintenance, PaymentService payments) {
        this.maintenance = maintenance;
        this.payments = payments;
    }

    /**
     * A cada 30 s: retoma pagamentos parados em CREATING (o provedor de Pix estava fora do ar ou a aplicação caiu
     * entre as fases da criação). Conclui (PENDING) ou, se o prazo venceu, expira.
     */
    @Scheduled(fixedDelay = 30, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void resumeStuckCreations() {
        int resolved = RequestIds.runWithNewId("job",
                () -> payments.resumeStuckCreations(STUCK_CREATION_GRACE, STUCK_CREATION_BATCH));
        if (resolved > 0) {
            log.info("Resolved {} payment creation(s) stuck in CREATING", resolved);
        }
    }

    static final int RECONCILIATION_BATCH = 20;

    /**
     * A cada 30 s: conciliação com o PSP (Etapa 7d). Consulta os PENDING com cobrança no PSP: pagos → PAID; vencidos são
     * cancelados no PSP antes de virar EXPIRED. Sem efeito com o provedor sandbox (não há cobranças externas).
     */
    @Scheduled(fixedDelay = 30, initialDelay = 15, timeUnit = TimeUnit.SECONDS)
    public void reconcileProviderCharges() {
        int changed = RequestIds.runWithNewId("job", () -> payments.reconcileProviderCharges(RECONCILIATION_BATCH));
        if (changed > 0) {
            log.info("Reconciled {} payment(s) with the Pix provider", changed);
        }
    }

    /** A cada 1 minuto: PENDING vencidos (só cobranças locais) -> EXPIRED. */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void expireOverduePayments() {
        // Cada execução tem o seu id de correlação (job-xxxxxxxx): as linhas dela podem ser filtradas juntas.
        int expired = RequestIds.runWithNewId("job", maintenance::expireOverduePayments);
        if (expired > 0) {
            log.info("Expired {} overdue payment(s)", expired);
        }
    }

    /** A cada 1 hora: apaga chaves de idempotência vencidas. */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpiredIdempotencyKeys() {
        RequestIds.runWithNewId("job", () -> {
            int purged = maintenance.purgeExpiredIdempotencyKeys();
            if (purged > 0) {
                log.info("Purged {} expired idempotency key(s)", purged);
            }
            return purged;
        });
    }
}
