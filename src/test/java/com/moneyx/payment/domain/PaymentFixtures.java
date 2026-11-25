package com.moneyx.payment.domain;

import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.SandboxPixProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Pagamentos prontos para os testes. Desde a Fase 7 (Etapa 7c) um pagamento nasce CREATING e só vira PENDING quando
 * a cobrança é anexada; aqui a cobrança vem do provedor de sandbox (local, sem rede).
 */
public final class PaymentFixtures {

    private static final SandboxPixProvider SANDBOX = new SandboxPixProvider();

    private PaymentFixtures() {
    }

    /** Recém-criado, sem cobrança (CREATING). */
    public static Payment creating(UUID merchantId, long amount, String description, String externalReference,
                                   Duration expiresIn, Instant now) {
        return Payment.create(merchantId, amount, PaymentCurrency.BRL, description, externalReference, expiresIn,
                SandboxPixProvider.NAME, now);
    }

    /** Com a cobrança do sandbox anexada (PENDING), como o PaymentService deixa um pagamento novo. */
    public static Payment pending(UUID merchantId, long amount, String description, String externalReference,
                                  Duration expiresIn, Instant now) {
        return charged(creating(merchantId, amount, description, externalReference, expiresIn, now), now);
    }

    public static Payment pending(UUID merchantId, long amount, Instant now) {
        return pending(merchantId, amount, null, null, null, now);
    }

    /** Anexa a cobrança do sandbox a um pagamento CREATING. */
    public static Payment charged(Payment payment, Instant now) {
        payment.attachPixCharge(SANDBOX.createCharge(new PixChargeRequest(payment.getId(), payment.getAmount(),
                payment.requestedExpiration(), payment.getDescription())), now);
        return payment;
    }
}
