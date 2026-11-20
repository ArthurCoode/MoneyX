package com.moneyx.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limite do cadastro público (moneyx.rate-limit.signup.*).
 *
 * @param capacity     tentativas em rajada por IP (tamanho do balde)
 * @param refillPeriod tempo para recuperar 1 tentativa
 * @param maxKeys      teto de IPs acompanhados em memória (proteção contra esgotamento de memória)
 */
@ConfigurationProperties("moneyx.rate-limit.signup")
public record SignupRateLimitProperties(
        @DefaultValue("5") int capacity,
        @DefaultValue("2m") Duration refillPeriod,
        @DefaultValue("100000") int maxKeys
) {
}
