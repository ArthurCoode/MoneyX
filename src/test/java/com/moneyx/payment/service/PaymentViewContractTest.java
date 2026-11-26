package com.moneyx.payment.service;

import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Os valores documentados no contrato OpenAPI precisam ser exatamente os dos enums (um status novo não pode sumir). */
class PaymentViewContractTest {

    @Test
    void documentedStatusesAreExactlyThePaymentStatusValues() throws Exception {
        assertThat(allowed("status")).containsExactlyInAnyOrder(
                Arrays.stream(PaymentStatus.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void documentedCurrenciesAreExactlyThePaymentCurrencyValues() throws Exception {
        assertThat(allowed("currency")).containsExactlyInAnyOrder(
                Arrays.stream(PaymentCurrency.values()).map(Enum::name).toArray(String[]::new));
    }

    /** A anotação de um componente de record chega ao campo privado gerado pelo compilador. */
    private static String[] allowed(String component) throws Exception {
        Schema schema = PaymentView.class.getDeclaredField(component).getAnnotation(Schema.class);
        assertThat(schema).as("@Schema em " + component).isNotNull();
        return schema.allowableValues();
    }
}
