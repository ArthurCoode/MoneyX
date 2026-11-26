package com.moneyx.security.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Cria limitadores no armazenamento configurado (moneyx.rate-limit.store): memory ou redis. Cada limitador tem o seu
 * prefixo no Redis (ex.: moneyx:ratelimit:login-ip:), então baldes de propósitos diferentes nunca se misturam.
 */
public class RateLimiterFactory {

    private final String store;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final Clock clock;
    private final int maxKeysInMemory;

    public RateLimiterFactory(String store, ObjectProvider<StringRedisTemplate> redis, Clock clock, int maxKeysInMemory) {
        if (!"memory".equals(store) && !"redis".equals(store)) {
            throw new IllegalArgumentException("moneyx.rate-limit.store must be memory or redis, got: " + store);
        }
        this.store = store;
        this.redis = redis;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxKeysInMemory = maxKeysInMemory;
    }

    /**
     * @param name         propósito (vira parte da chave no Redis), ex.: "signup", "login-ip"
     * @param capacity     tentativas em rajada
     * @param refillPeriod tempo para recuperar 1 tentativa
     */
    public RateLimiter create(String name, int capacity, Duration refillPeriod) {
        return "redis".equals(store)
                ? new RedisTokenBucketRateLimiter(redis.getObject(), "moneyx:ratelimit:" + name + ":", capacity,
                refillPeriod, clock)
                : new TokenBucketRateLimiter(capacity, refillPeriod, maxKeysInMemory, clock);
    }
}
