package com.moneyx.payment.api.dto;

import com.moneyx.payment.domain.Payment;

import java.time.Instant;
import java.util.UUID;

/** Representação pública de um pagamento. O valor é sempre em centavos. */
public record PaymentResponse(
        UUID id,
        String status,
        long amount,
        String currency,
        String description,
        String externalReference,
        Instant expiresAt,
        Instant paidAt,
        Instant canceledAt,
        Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency().name(),
                payment.getDescription(),
                payment.getExternalReference(),
                payment.getExpiresAt(),
                payment.getPaidAt(),
                payment.getCanceledAt(),
                payment.getCreatedAt()
        );
    }
}
