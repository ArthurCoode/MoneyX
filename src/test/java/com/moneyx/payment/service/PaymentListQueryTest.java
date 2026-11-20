package com.moneyx.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentListQueryTest {

    @Test
    void defaultsToTenItems() {
        assertThat(PaymentListQuery.firstPage().limit()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsLimitsWithinRange(int limit) {
        assertThat(new PaymentListQuery(limit, null, null, null).limit()).isEqualTo(limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void rejectsLimitsOutOfRange(int limit) {
        assertThatThrownBy(() -> new PaymentListQuery(limit, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void blankExternalReferenceMeansNoFilter() {
        assertThat(new PaymentListQuery(null, null, null, "   ").externalReference()).isNull();
    }

    @Test
    void externalReferenceIsTrimmed() {
        assertThat(new PaymentListQuery(null, null, null, " order-1 ").externalReference()).isEqualTo("order-1");
    }

    @Test
    void rejectsExternalReferenceLongerThanTheStoredMaximum() {
        assertThatThrownBy(() -> new PaymentListQuery(null, null, null, "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
