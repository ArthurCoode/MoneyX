package com.moneyx.security.ratelimit;

import java.time.Duration;

/**
 * Limitador por chave (token bucket). Duas implementações, escolhidas por moneyx.rate-limit.store:
 * - memory (padrão): TokenBucketRateLimiter, no processo. Correto com UMA instância da aplicação;
 * - redis: RedisTokenBucketRateLimiter, compartilhado entre instâncias (cada instância em memória teria o seu próprio
 *   balde, e N instâncias multiplicariam o limite por N).
 */
public interface RateLimiter {

    /** Resultado de uma tentativa. retryAfter só é significativo quando allowed == false. */
    record Decision(boolean allowed, Duration retryAfter) {
    }

    Decision tryAcquire(String key);
}
