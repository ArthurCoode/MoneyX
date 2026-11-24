package com.moneyx.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Limite ACUMULADO de volume por comerciante (moneyx.payments.volume-limit.*), em centavos.
 *
 * É um controle de risco: com uma API Key roubada, alguém poderia criar cobranças em massa. O limite por OPERAÇÃO
 * (R$ 100 mil, no domínio) não impede isso; o acumulado por dia/mês impede.
 *
 * @param daily   soma máxima no dia (padrão R$ 200.000,00)
 * @param monthly soma máxima no mês (padrão R$ 2.000.000,00)
 * @param zone    fuso que define onde começa o "dia" e o "mês" (padrão America/Sao_Paulo). Sempre um ZoneId,
 *                nunca um deslocamento fixo como -03:00: as regras de fuso podem mudar (horário de verão).
 */
@ConfigurationProperties("moneyx.payments.volume-limit")
public record PaymentVolumeLimitProperties(
        @DefaultValue("20000000") long daily,
        @DefaultValue("200000000") long monthly,
        @DefaultValue("America/Sao_Paulo") ZoneId zone
) {

    public PaymentVolumeLimitProperties {
        if (daily < 1) {
            throw new IllegalArgumentException("daily volume limit must be positive");
        }
        if (monthly < daily) {
            throw new IllegalArgumentException("monthly volume limit must be at least the daily limit");
        }
    }
}
