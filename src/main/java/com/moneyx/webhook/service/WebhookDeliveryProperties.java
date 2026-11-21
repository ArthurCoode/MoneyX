package com.moneyx.webhook.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuração da entrega (moneyx.webhooks.delivery.*). Os valores padrão são os decididos para a v1.
 *
 * @param connectTimeout tempo máximo para abrir a conexão com a loja
 * @param requestTimeout tempo máximo para a loja responder
 * @param lease          duração da reserva; precisa ser MAIOR que connect + request, senão a reserva venceria
 *                       com o envio ainda em andamento e outra execução enviaria de novo
 * @param batchSize      entregas reservadas por vez
 * @param retryJitter    variação máxima, para cima ou para baixo, das esperas entre tentativas (0.1 = ±10%).
 *                       Os testes usam 0 para conferir horários exatos.
 * @param retention      por quanto tempo uma entrega FINALIZADA é guardada (consulta e reenvio manual); depois disso
 *                       é apagada pela retenção. Mínimo de 7 dias: mais que a agenda inteira de retentativas (~2,7 dias).
 */
@ConfigurationProperties("moneyx.webhooks.delivery")
public record WebhookDeliveryProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout,
        @DefaultValue("30s") Duration lease,
        @DefaultValue("10") int batchSize,
        @DefaultValue("0.1") double retryJitter,
        @DefaultValue("30d") Duration retention
) {

    public WebhookDeliveryProperties {
        if (retryJitter < 0 || retryJitter > 0.5) {
            throw new IllegalArgumentException("retryJitter must be between 0 and 0.5");
        }
        if (lease.compareTo(connectTimeout.plus(requestTimeout)) <= 0) {
            throw new IllegalArgumentException("lease must be longer than connectTimeout + requestTimeout");
        }
        if (retention.compareTo(Duration.ofDays(7)) < 0) {
            throw new IllegalArgumentException("retention must be at least 7 days");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
    }
}
