package com.moneyx.security.ratelimit;

import com.moneyx.support.MutableClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Token bucket em um Redis REAL (Testcontainers). Mesmas regras do TokenBucketRateLimiterTest, mais o que só existe
 * aqui: atomicidade entre "instâncias" (dois limitadores no mesmo Redis), expiração da chave e Redis fora do ar.
 */
class RedisTokenBucketRateLimiterTest {

    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00.123456Z"));
    private String prefix;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connections.afterPropertiesSet();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stopRedis() {
        connections.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void isolate() {
        prefix = "test:" + UUID.randomUUID() + ":";
    }

    private RedisTokenBucketRateLimiter limiter() {
        return new RedisTokenBucketRateLimiter(redis, prefix, 5, TWO_MINUTES, clock);
    }

    @Test
    void allowsTheBurstThenRefusesWithTheExactWait() {
        RedisTokenBucketRateLimiter limiter = limiter();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(30));
        RateLimiter.Decision refused = limiter.tryAcquire("1.2.3.4");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isEqualTo(Duration.ofSeconds(90));
        assertThat(limiter.tryAcquire("5.6.7.8").allowed()).isTrue(); // outro IP, outro balde
    }

    @Test
    void anHonestClientWaitingTheRetryAfterIsAlwaysAccepted() {
        RedisTokenBucketRateLimiter limiter = limiter();
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("honest");
        }
        // Taxa sustentada: 100 tentativas, cada uma no instante exato indicado. Erro acumulado apareceria aqui.
        for (int i = 0; i < 100; i++) {
            RateLimiter.Decision refused = limiter.tryAcquire("honest");
            assertThat(refused.allowed()).isFalse();
            clock.advance(refused.retryAfter());
            assertThat(limiter.tryAcquire("honest").allowed()).as("tentativa %d", i).isTrue();
        }
    }

    @Test
    void creditNeverGoesAboveTheCapacity() {
        RedisTokenBucketRateLimiter limiter = limiter();
        limiter.tryAcquire("idle");
        clock.advance(Duration.ofDays(3));
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire("idle").allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(5);
    }

    @Test
    void theKeyExpiresWhenTheBucketWouldBeFullAgain() {
        limiter().tryAcquire("ttl");
        Long ttlMillis = redis.getExpire(prefix + "ttl", TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isBetween(Duration.ofMinutes(10).toMillis(), Duration.ofMinutes(10).plusSeconds(1).toMillis());
    }

    @Test
    void twoInstancesSharingRedisNeverGiveMoreThanTheCapacity() throws Exception {
        // 2 "instâncias" da aplicação x 25 threads disputando o MESMO IP.
        List<RedisTokenBucketRateLimiter> instances = List.of(limiter(), limiter());
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            RedisTokenBucketRateLimiter instance = instances.get(i % 2);
            results.add(pool.submit(() -> {
                go.await();
                return instance.tryAcquire("shared").allowed();
            }));
        }
        go.countDown();
        int allowed = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                allowed++;
            }
        }
        pool.shutdown();
        assertThat(allowed).isEqualTo(5);
    }

    @Test
    void whenRedisIsUnreachableTheRequestIsAllowed() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        dead.afterPropertiesSet();
        try {
            RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(
                    new StringRedisTemplate(dead), prefix, 1, TWO_MINUTES, clock);
            assertThat(limiter.tryAcquire("x").allowed()).isTrue();
            assertThat(limiter.tryAcquire("x").allowed()).isTrue();
        } finally {
            dead.destroy();
        }
    }
}
