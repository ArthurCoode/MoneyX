package com.moneyx.webhook.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Quando tentar de novo uma entrega que falhou (backoff exponencial com jitter).
 *
 *   tentativa 1 (imediata) falhou -> espera 1 min -> tentativa 2 -> 5 min -> 3 -> 30 min -> 4 -> 2 h -> 5 -> 5 h
 *   -> 6 -> 10 h -> 7 -> 24 h -> 8 -> 24 h -> 9 (última). Total: ~2,7 dias.
 *
 * - Exponencial: quedas rápidas se resolvem nas primeiras tentativas; uma loja fora do ar por horas não é bombardeada.
 * - Jitter: cada espera varia um pouco para cima ou para baixo. Sem isso, 10 mil entregas que falharam no mesmo
 *   minuto voltariam no MESMO segundo e derrubariam a loja de novo ("thundering herd").
 * - destination_not_allowed nunca é reagendada: é uma decisão de segurança (SSRF), não uma falha temporária.
 *
 * Classe pura: o jitter chega como parâmetro (quem chama sorteia), então os testes conferem horários exatos.
 */
public final class WebhookRetryPolicy {

    /** Espera depois da tentativa N que falhou (índice 0 = depois da 1ª). */
    public static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(5),
            Duration.ofHours(10),
            Duration.ofHours(24),
            Duration.ofHours(24));

    public static final int MAX_ATTEMPTS = DELAYS.size() + 1;

    private WebhookRetryPolicy() {
    }

    /**
     * @param failedAttempt número da tentativa que acabou de falhar (1 = a primeira)
     * @param jitter        variação relativa já sorteada, ex.: 0.07 = +7%, -0.1 = -10% (0 = sem variação)
     * @return o horário da próxima tentativa, ou vazio se é hora de desistir
     */
    public static Optional<Instant> nextAttemptAt(int failedAttempt, WebhookDeliveryError error, Instant now, double jitter) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("failedAttempt must be at least 1");
        }
        if (error == WebhookDeliveryError.DESTINATION_NOT_ALLOWED || failedAttempt >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        Duration base = DELAYS.get(failedAttempt - 1);
        long millis = Math.round(base.toMillis() * (1 + jitter));
        return Optional.of(now.plusMillis(millis));
    }
}
