package com.moneyx.payment.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.service.EventRecorded;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Log e métrica de cada transição de pagamento (criado, pago, cancelado, expirado), DEPOIS do commit.
 *
 * O mesmo mecanismo da Fase 5, com o objetivo oposto:
 * - as entregas de webhook PRECISAM nascer dentro da transação (@EventListener): se o pagamento for confirmado,
 *   a entrega existe;
 * - log e métrica devem contar só o que FOI confirmado (@TransactionalEventListener AFTER_COMMIT): um rollback não
 *   pode inflar "pagamentos pagos". Se a aplicação cair entre o commit e esta linha, perde-se só uma contagem
 *   aproximada, nunca um dado de negócio.
 *
 * Métrica moneyx.payments.events{type}: a etiqueta é o TIPO (4 valores possíveis). Nunca ids como etiqueta: cada valor
 * distinto vira uma série nova no armazenamento de métricas (explosão de cardinalidade).
 */
@Component
class PaymentObservability {

    private static final Logger log = LoggerFactory.getLogger(PaymentObservability.class);

    private final MeterRegistry registry;

    PaymentObservability(MeterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onEventCommitted(EventRecorded recorded) {
        Event event = recorded.event();
        String type = event.getType().value();
        if (!type.startsWith("payment.")) {
            return;
        }
        // Só ids e o tipo: nunca valor de documento, e-mail, chave ou segredo.
        log.info("{} payment={} merchant={} event={}", type, event.getResourceId(), event.getMerchantId(), event.getId());
        Counter.builder("moneyx.payments.events")
                .description("Transições de pagamento confirmadas, por tipo de evento")
                .tag("type", type)
                .register(registry)
                .increment();
    }
}
