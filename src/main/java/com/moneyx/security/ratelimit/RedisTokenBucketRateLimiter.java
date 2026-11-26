package com.moneyx.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Token bucket no REDIS, compartilhado por todas as instâncias da aplicação (moneyx.rate-limit.store=redis).
 *
 * Mesma regra do TokenBucketRateLimiter (balde de capacity fichas, 1 ficha a cada refillPeriod), com a conta feita
 * por um script Lua: o Redis executa o script inteiro de forma ATÔMICA (nenhum outro comando entra no meio), então
 * duas instâncias nunca leem o mesmo crédito e gastam a mesma ficha.
 *
 * Crédito em MICROSSEGUNDOS inteiros (1 ficha = refillPeriod em µs). Não em nanos: os números do Lua do Redis são
 * double, exatos só até 2^53 (~9 x 10^15); o instante em µs desde 1970 (~1,8 x 10^15) cabe, em ns (~1,8 x 10^18) não.
 * Mesma lição do bug de 2026-11-20: contagem que acumula frações nunca em ponto flutuante com perda.
 *
 * O instante vem do Clock da aplicação (os testes o controlam). Com várias instâncias, os relógios precisam estar
 * sincronizados (NTP); uma diferença de segundos só adianta ou atrasa a recarga nesse tanto.
 *
 * Cada chave expira sozinha (PEXPIRE) quando o balde estaria cheio de novo: a memória do Redis não cresce para
 * sempre (substitui o teto de chaves da versão em memória).
 *
 * Redis fora do ar: a tentativa é PERMITIDA (fail-open) e um aviso vai para o log. O limite protege o cadastro
 * contra abuso; ele não pode derrubar o cadastro inteiro quando a infraestrutura dele falha.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    /** KEYS[1] = balde; ARGV = agora (µs), ficha (µs), crédito máximo (µs), validade da chave (ms). */
    static final String SCRIPT = """
            local now = tonumber(ARGV[1])
            local token = tonumber(ARGV[2])
            local max = tonumber(ARGV[3])
            local state = redis.call('HMGET', KEYS[1], 'credit', 'updated')
            local credit = max
            if state[1] then
              local elapsed = now - tonumber(state[2])
              if elapsed < 0 then elapsed = 0 end
              credit = math.min(max, tonumber(state[1]) + elapsed)
            end
            local allowed = 0
            local wait = 0
            if credit >= token then
              credit = credit - token
              allowed = 1
            else
              wait = token - credit
            end
            redis.call('HSET', KEYS[1], 'credit', string.format('%.0f', credit), 'updated', string.format('%.0f', now))
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return {allowed, wait}
            """;

    private static final DefaultRedisScript<List> REDIS_SCRIPT = new DefaultRedisScript<>(SCRIPT, List.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Clock clock;
    private final long tokenMicros;
    private final long maxCreditMicros;
    private final long keyTtlMillis;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, String keyPrefix, int capacity, Duration refillPeriod,
                                       Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (refillPeriod == null || refillPeriod.toNanos() < 1_000) {
            throw new IllegalArgumentException("refillPeriod must be at least 1 microsecond");
        }
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenMicros = refillPeriod.toNanos() / 1_000;
        this.maxCreditMicros = Math.multiplyExact(tokenMicros, capacity);
        // Depois disso o balde estaria cheio de qualquer forma: apagar a chave não muda nenhuma decisão.
        this.keyTtlMillis = maxCreditMicros / 1_000 + 1_000;
    }

    @Override
    public Decision tryAcquire(String key) {
        Objects.requireNonNull(key, "key must not be null");
        long nowMicros = ChronoUnit.MICROS.between(java.time.Instant.EPOCH, clock.instant());
        try {
            List<?> result = redis.execute(REDIS_SCRIPT, List.of(keyPrefix + key),
                    Long.toString(nowMicros), Long.toString(tokenMicros), Long.toString(maxCreditMicros),
                    Long.toString(keyTtlMillis));
            boolean allowed = ((Number) result.get(0)).longValue() == 1;
            long waitMicros = ((Number) result.get(1)).longValue();
            return new Decision(allowed, allowed ? Duration.ZERO : Duration.of(waitMicros, ChronoUnit.MICROS));
        } catch (DataAccessException e) {
            log.warn("rate_limit.store_unavailable store=redis error={} (request allowed)", e.getClass().getSimpleName());
            return new Decision(true, Duration.ZERO);
        }
    }
}
