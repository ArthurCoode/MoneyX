package com.moneyx.payment.exception;

import java.time.Instant;

/**
 * O pagamento faria o comerciante passar do limite acumulado do período.
 *
 * Carrega só dados do PRÓPRIO comerciante (limite, quanto ainda cabe, quando o período recomeça).
 */
public class VolumeLimitExceededException extends RuntimeException {

    /** Período cujo limite seria ultrapassado. O nome em minúsculas é o valor público da API. */
    public enum Period {
        DAILY, MONTHLY;

        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Period period;
    private final long limit;
    private final long remaining;
    private final Instant resetsAt;

    public VolumeLimitExceededException(Period period, long limit, long remaining, Instant resetsAt) {
        super("This payment would exceed the " + period.wireName() + " payment volume limit");
        this.period = period;
        this.limit = limit;
        this.remaining = remaining;
        this.resetsAt = resetsAt;
    }

    public Period getPeriod() {
        return period;
    }

    public long getLimit() {
        return limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public Instant getResetsAt() {
        return resetsAt;
    }
}
