package com.moneyx.payment.provider.mercadopago;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Assinatura dos avisos do MP. Vetores calculados À PARTE, com o hmac do Python (não com este código). */
class MercadoPagoWebhookSignatureTest {

    private static final String SECRET = "test-secret-123";
    private static final String ORDER = "ORD01M28P44G5FG8RJPM579EH56FV";
    private static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
    /** Python: hmac.new(b'test-secret-123', b'id:ord01m28p44g5fg8rjpm579eh56fv;request-id:bb56...;ts:1704908010;', sha256) */
    private static final String V1 = "424efcc41c924515184649825d536b2df0223d15d53d64b2c741fa904a26983f";

    @Test
    void acceptsTheSignatureComputedIndependently() {
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, "ts=1704908010,v1=" + V1, REQUEST_ID, ORDER)).isTrue();
        // espaços e ordem dos campos não importam
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, "v1=" + V1 + ", ts=1704908010", REQUEST_ID, ORDER)).isTrue();
    }

    @Test
    void theAlphanumericIdIsSignedInLowercase() {
        assertThat(MercadoPagoWebhookSignature.manifest(ORDER, REQUEST_ID, "1"))
                .isEqualTo("id:ord01m28p44g5fg8rjpm579eh56fv;request-id:" + REQUEST_ID + ";ts:1;");
    }

    @Test
    void aMissingPartIsOmittedFromTheManifest() {
        // Python: hmac.new(b'test-secret-123', b'request-id:abc;ts:1704908010;', sha256)
        assertThat(MercadoPagoWebhookSignature.verify(SECRET,
                "ts=1704908010,v1=4a8ed7fbf394fc6fca3a528a98019db88be26f0c22091ee156c02ada56757e6d", "abc", null)).isTrue();
    }

    @Test
    void rejectsAnythingThatDoesNotMatch() {
        String header = "ts=1704908010,v1=" + V1;
        assertThat(MercadoPagoWebhookSignature.verify("other-secret", header, REQUEST_ID, ORDER)).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, header, REQUEST_ID, "ORD01M28P44G5FG8RJPM579EH56FX")).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, header, "another-request", ORDER)).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, "ts=1704908011,v1=" + V1, REQUEST_ID, ORDER)).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, "v1=" + V1, REQUEST_ID, ORDER)).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, "ts=1704908010,v1=abc", REQUEST_ID, ORDER)).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify(SECRET, null, REQUEST_ID, ORDER)).isFalse();
        assertThat(MercadoPagoWebhookSignature.verify("", header, REQUEST_ID, ORDER)).isFalse();
    }
}
