package com.moneyx.security.ratelimit;

import com.moneyx.support.MutableClock;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketRateLimiterTest {

    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T12:00:00Z"));
    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, TWO_MINUTES, 1_000, clock);

    @Test
    void allowsABurstUpToTheCapacityThenRefuses() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        }

        TokenBucketRateLimiter.Decision refused = limiter.tryAcquire("1.2.3.4");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isEqualTo(TWO_MINUTES); // a próxima ficha chega em 2 min
    }

    @Test
    void recoversOneTokenPerRefillPeriod() {
        exhaust("1.2.3.4");

        clock.advance(Duration.ofSeconds(119));
        TokenBucketRateLimiter.Decision stillRefused = limiter.tryAcquire("1.2.3.4");
        assertThat(stillRefused.allowed()).isFalse();
        assertThat(stillRefused.retryAfter()).isEqualTo(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isFalse(); // só 1 ficha recuperada
    }

    /** O robô não passa da MÉDIA: depois da rajada, no máximo 1 tentativa a cada 2 minutos. */
    @Test
    void sustainedRateIsOnePerRefillPeriod() {
        exhaust("1.2.3.4");
        int allowed = 0;
        for (int second = 1; second <= 600; second++) { // 10 minutos, uma tentativa por segundo
            clock.advance(Duration.ofSeconds(1));
            if (limiter.tryAcquire("1.2.3.4").allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(5); // 600 s / 120 s
    }

    /**
     * O Retry-After não pode mentir: um cliente honesto que volta EXATAMENTE no tempo indicado é aceito, mesmo depois
     * de várias tentativas recusadas no meio (com ponto flutuante, frações acumuladas atrasavam a ficha).
     */
    @Test
    void anHonestClientReturningAtRetryAfterIsAccepted() {
        exhaust("1.2.3.4");
        for (int i = 0; i < 37; i++) {
            clock.advance(Duration.ofMillis(1_234)); // tentativas recusadas em instantes "quebrados"
            limiter.tryAcquire("1.2.3.4");
        }

        TokenBucketRateLimiter.Decision refused = limiter.tryAcquire("1.2.3.4");
        clock.advance(refused.retryAfter());

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
    }

    @Test
    void refillNeverExceedsTheCapacity() {
        limiter.tryAcquire("1.2.3.4");
        clock.advance(Duration.ofDays(1));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isFalse();
    }

    @Test
    void keysAreIndependent() {
        exhaust("1.2.3.4");

        assertThat(limiter.tryAcquire("5.6.7.8").allowed()).isTrue();
    }

    // ---------- memória ----------

    /** Um balde cheio é igual a um inexistente: ao atingir o teto, ele é removido sem perder informação. */
    @Test
    void fullBucketsAreEvictedWhenTheKeyLimitIsReached() {
        TokenBucketRateLimiter small = new TokenBucketRateLimiter(5, TWO_MINUTES, 3, clock);
        small.tryAcquire("a");
        small.tryAcquire("b");
        small.tryAcquire("c");
        clock.advance(Duration.ofMinutes(10)); // a, b e c voltaram a ficar cheios

        assertThat(small.tryAcquire("d").allowed()).isTrue();
        assertThat(small.trackedKeys()).isEqualTo(1);
    }

    /** Ataque com mais IPs do que o teto dentro da janela: novas chaves são negadas (fail-closed). */
    @Test
    void newKeysAreRefusedWhenTheLimitIsReachedByActiveBuckets() {
        TokenBucketRateLimiter small = new TokenBucketRateLimiter(5, TWO_MINUTES, 3, clock);
        small.tryAcquire("a");
        small.tryAcquire("b");
        small.tryAcquire("c");

        assertThat(small.tryAcquire("d").allowed()).isFalse();
        assertThat(small.trackedKeys()).isEqualTo(3);
        assertThat(small.tryAcquire("a").allowed()).isTrue(); // quem já é acompanhado continua sendo atendido
    }

    // ---------- concorrência ----------

    /** 50 tentativas simultâneas do mesmo IP: exatamente 5 passam (nenhuma ficha é gasta duas vezes). */
    @RepeatedTest(5)
    void concurrentAttemptsNeverExceedTheCapacity() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAcquire("9.9.9.9").allowed();
                }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------- validação e Retry-After ----------

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0, TWO_MINUTES, 1, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(1, Duration.ZERO, 1, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(1, TWO_MINUTES, 0, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Retry-After em segundos inteiros, SEMPRE arredondado para cima (para baixo, o cliente voltaria cedo demais). */
    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        assertThat(new RateLimitExceededException(Duration.ofSeconds(120)).retryAfterSeconds()).isEqualTo(120);
        assertThat(new RateLimitExceededException(Duration.ofMillis(119_001)).retryAfterSeconds()).isEqualTo(120);
        assertThat(new RateLimitExceededException(Duration.ofMillis(1)).retryAfterSeconds()).isEqualTo(1);
        assertThat(new RateLimitExceededException(Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
    }

    private void exhaust(String key) {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key);
        }
    }
}
