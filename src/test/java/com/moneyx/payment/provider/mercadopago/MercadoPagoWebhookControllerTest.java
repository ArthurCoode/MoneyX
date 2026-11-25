package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.exception.PaymentProviderUnavailableException;
import com.moneyx.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Aviso do MP (Etapa 7e): o aviso só dispara a conciliação, e só com assinatura válida. */
class MercadoPagoWebhookControllerTest {

    private static final String SECRET = "test-secret-123";
    private static final String ORDER = "ORD01M28P44G5FG8RJPM579EH56FV";
    private static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
    private static final String SIGNATURE =
            "ts=1704908010,v1=424efcc41c924515184649825d536b2df0223d15d53d64b2c741fa904a26983f";

    private final PaymentService payments = Mockito.mock(PaymentService.class);

    private MercadoPagoWebhookController controller(String secret) {
        return new MercadoPagoWebhookController(new MercadoPagoProperties(URI.create("http://x"), "APP_USR-t",
                Duration.ofSeconds(1), Duration.ofSeconds(1), "a@b.c", "", secret), payments);
    }

    @Test
    void aValidNoticeReconcilesTheOrder() {
        when(payments.reconcileProviderOrder(ORDER)).thenReturn(true);

        assertThat(controller(SECRET).receive(ORDER, "order", SIGNATURE, REQUEST_ID).getStatusCode().value()).isEqualTo(200);
        verify(payments).reconcileProviderOrder(ORDER);
    }

    @Test
    void anUnknownOrderIsAcknowledgedSoTheProviderStopsRetrying() {
        when(payments.reconcileProviderOrder(ORDER)).thenReturn(false);
        assertThat(controller(SECRET).receive(ORDER, "order", SIGNATURE, REQUEST_ID).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void anInvalidSignatureIsRejectedWithoutTouchingPayments() {
        assertThat(controller(SECRET).receive(ORDER, "order", SIGNATURE.replace("424e", "424f"), REQUEST_ID)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(controller(SECRET).receive(ORDER, "order", null, REQUEST_ID).getStatusCode().value()).isEqualTo(401);
        verify(payments, never()).reconcileProviderOrder(any());
    }

    @Test
    void withoutTheSecretTheEndpointDoesNotExist() {
        assertThat(controller("").receive(ORDER, "order", SIGNATURE, REQUEST_ID).getStatusCode().value()).isEqualTo(404);
        verify(payments, never()).reconcileProviderOrder(any());
    }

    @Test
    void otherTopicsAreAcknowledgedAndIgnored() {
        assertThat(controller(SECRET).receive(ORDER, "payment", SIGNATURE, REQUEST_ID).getStatusCode().value()).isEqualTo(200);
        verify(payments, never()).reconcileProviderOrder(any());
    }

    @Test
    void whenTheProviderCannotBeQueriedTheNoticeMustBeRetried() {
        when(payments.reconcileProviderOrder(ORDER)).thenThrow(new PaymentProviderUnavailableException(null));
        assertThat(controller(SECRET).receive(ORDER, "order", SIGNATURE, REQUEST_ID).getStatusCode().value()).isEqualTo(503);
    }
}
