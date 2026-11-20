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
 */
@ConfigurationProperties("moneyx.webhooks.delivery")
public record WebhookDeliveryProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout,
        @DefaultValue("30s") Duration lease,
        @DefaultValue("10") int batchSize
) {

    public WebhookDeliveryProperties {
        if (lease.compareTo(connectTimeout.plus(requestTimeout)) <= 0) {
            throw new IllegalArgumentException("lease must be longer than connectTimeout + requestTimeout");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
    }
}
