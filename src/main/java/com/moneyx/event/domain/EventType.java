package com.moneyx.event.domain;

import java.util.Arrays;

/**
 * Catálogo público de tipos de evento, no formato "recurso.acao" (padrão Stripe).
 *
 * O valor (ex.: "payment.paid") faz parte do CONTRATO com o comerciante: é gravado no banco, devolvido
 * pela API e, no futuro, enviado nos webhooks. Nunca renomear um tipo existente; criar um novo.
 */
public enum EventType {
    PAYMENT_CREATED("payment.created"),
    PAYMENT_PAID("payment.paid"),
    PAYMENT_CANCELED("payment.canceled"),
    PAYMENT_EXPIRED("payment.expired");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** @throws IllegalArgumentException se o valor não for um tipo conhecido */
    public static EventType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + value));
    }
}
