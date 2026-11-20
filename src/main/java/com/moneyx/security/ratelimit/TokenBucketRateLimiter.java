package com.moneyx.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador "token bucket" (balde de fichas), em memória.
 *
 * Cada chave (IP) tem um balde com até {@code capacity} fichas. Cada tentativa gasta 1 ficha; o balde recupera
 * 1 ficha a cada {@code refillPeriod}, até o máximo. Permite RAJADAS curtas (um humano errando o formulário) e
 * mantém a MÉDIA baixa (um robô não passa de 1 tentativa por período).
 *
 * Concorrência: a leitura-cálculo-gravação de cada balde é feita dentro de ConcurrentHashMap.compute, que é atômico
 * POR CHAVE. Duas requisições simultâneas do mesmo IP nunca gastam a mesma ficha.
 *
 * Memória: um atacante que troca de IP a cada requisição criaria baldes sem fim. Por isso existe um teto de chaves.
 * Ao atingi-lo, os baldes CHEIOS são removidos (um balde cheio é idêntico a um balde inexistente, então nada se perde).
 * Se mesmo assim o teto continuar atingido (ataque com mais IPs que o teto dentro de uma janela), novas chaves são
 * NEGADAS (fail-closed) e um alerta vai para o log: para um cadastro público, negar é mais seguro que liberar.
 *
 * Só serve para UMA instância da aplicação. Com várias, cada uma teria o seu contador (o limite real se multiplicaria):
 * nesse cenário, mover o estado para um armazenamento compartilhado (ex.: Redis).
 */
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final int capacity;
    private final Duration refillPeriod;
    private final int maxKeys;
    private final Clock clock;
    /** Uma ficha = um refillPeriod, em NANOSSEGUNDOS inteiros. */
    private final long tokenNanos;
    /** Crédito máximo do balde: capacity fichas. */
    private final long maxCreditNanos;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Estado imutável de um balde: o CRÉDITO em nanossegundos (1 ficha = tokenNanos) e o instante do último cálculo.
     *
     * Por que long em nanos, e não double em fichas: somar 1/120 de ficha 120 vezes em ponto flutuante dá 0,9999999,
     * não 1. A ficha chegaria atrasada e o Retry-After mentiria (o cliente voltaria no horário indicado e ainda seria
     * recusado). É o mesmo motivo de o dinheiro ser guardado em centavos (long), nunca em double: conta exata.
     */
    private record Bucket(long creditNanos, Instant updatedAt) {
    }

    /** Resultado de uma tentativa. retryAfter só é significativo quando allowed == false. */
    public record Decision(boolean allowed, Duration retryAfter) {
    }

    public TokenBucketRateLimiter(int capacity, Duration refillPeriod, int maxKeys, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys must be at least 1");
        }
        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
        this.maxKeys = maxKeys;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenNanos = refillPeriod.toNanos();
        // multiplyExact: falha na partida se a configuração estourar o long, em vez de virar um número negativo.
        this.maxCreditNanos = Math.multiplyExact(tokenNanos, capacity);
    }

    public Decision tryAcquire(String key) {
        Objects.requireNonNull(key, "key must not be null");
        Instant now = clock.instant();

        if (!buckets.containsKey(key) && buckets.size() >= maxKeys) {
            evictFullBuckets(now);
            if (buckets.size() >= maxKeys) {
                log.warn("Rate limiter is tracking {} keys (limit {}); refusing new keys", buckets.size(), maxKeys);
                return new Decision(false, refillPeriod);
            }
        }

        // Guardado fora do compute: o resultado da decisão é calculado lá dentro, de forma atômica.
        Decision[] decision = new Decision[1];
        buckets.compute(key, (k, current) -> {
            long credit = current == null ? maxCreditNanos : refill(current, now);
            if (credit >= tokenNanos) {
                decision[0] = new Decision(true, Duration.ZERO);
                return new Bucket(credit - tokenNanos, now);
            }
            decision[0] = new Decision(false, Duration.ofNanos(tokenNanos - credit)); // exatamente o que falta
            return new Bucket(credit, now);
        });
        return decision[0];
    }

    /** Quantas chaves estão sendo acompanhadas (para testes e métricas). */
    public int trackedKeys() {
        return buckets.size();
    }

    /** Crédito atual: o anterior + o tempo decorrido (1 ns de tempo = 1 ns de crédito), limitado ao máximo. */
    private long refill(Bucket bucket, Instant now) {
        long elapsedNanos = Math.max(0, Duration.between(bucket.updatedAt(), now).toNanos());
        long remainingRoom = maxCreditNanos - bucket.creditNanos();
        return elapsedNanos >= remainingRoom ? maxCreditNanos : bucket.creditNanos() + elapsedNanos;
    }

    private void evictFullBuckets(Instant now) {
        buckets.entrySet().removeIf(entry -> refill(entry.getValue(), now) >= maxCreditNanos);
    }
}
