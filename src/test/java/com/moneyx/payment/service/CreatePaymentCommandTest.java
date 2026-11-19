package com.moneyx.payment.service;

import com.moneyx.payment.domain.PaymentCurrency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreatePaymentCommandTest {

    private static final CreatePaymentCommand BASE =
            new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", 1800L);

    @Test
    void fingerprintIsADeterministicSha256() {
        String fingerprint = BASE.fingerprint();

        assertThat(fingerprint).matches("^[0-9a-f]{64}$");
        assertThat(new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", 1800L).fingerprint())
                .isEqualTo(fingerprint);
    }

    @Test
    void changingAnyFieldChangesTheFingerprint() {
        List<CreatePaymentCommand> variants = List.of(
                new CreatePaymentCommand(4991, PaymentCurrency.BRL, "Pedido 123", "order-123", 1800L),
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 124", "order-123", 1800L),
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-124", 1800L),
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", 1801L),
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, null, "order-123", 1800L),
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", null, 1800L),
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", null));

        assertThat(variants).allSatisfy(variant ->
                assertThat(variant.fingerprint()).isNotEqualTo(BASE.fingerprint()));
    }

    @Test
    void fieldBoundariesAreUnambiguous() {
        // Concatenando sem separador/tamanho, os dois casos virariam o mesmo texto "ab".
        CreatePaymentCommand first = new CreatePaymentCommand(4990, PaymentCurrency.BRL, "ab", null, null);
        CreatePaymentCommand second = new CreatePaymentCommand(4990, PaymentCurrency.BRL, "a", "b", null);
        // Mesmo um valor que imita o formato interno não colide.
        CreatePaymentCommand tricky = new CreatePaymentCommand(4990, PaymentCurrency.BRL, "1:a;1:b", null, null);
        CreatePaymentCommand plain = new CreatePaymentCommand(4990, PaymentCurrency.BRL, "a", "b", null);

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
        assertThat(tricky.fingerprint()).isNotEqualTo(plain.fingerprint());
    }

    @Test
    void nullAndEmptyStringAreDifferent() {
        CreatePaymentCommand withNull = new CreatePaymentCommand(4990, PaymentCurrency.BRL, null, null, null);
        CreatePaymentCommand withEmpty = new CreatePaymentCommand(4990, PaymentCurrency.BRL, "", null, null);

        assertThat(withNull.fingerprint()).isNotEqualTo(withEmpty.fingerprint());
    }
}
