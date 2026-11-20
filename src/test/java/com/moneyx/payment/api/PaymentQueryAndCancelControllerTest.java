package com.moneyx.payment.api;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.InvalidCursorException;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.service.PaymentListQuery;
import com.moneyx.payment.service.PaymentPage;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.moneyx.security.TestAuthentication.activeMerchant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentQueryAndCancelControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService service;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private MerchantService merchantService;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private Payment newPayment() {
        return Payment.create(merchantId, 4990, PaymentCurrency.BRL, "Pedido 1", "order-1", null, NOW);
    }

    // ---------- GET /v1/payments/{id} ----------

    @Test
    void getReturns200WithPayment() throws Exception {
        Payment payment = newPayment();
        when(service.get(merchantId, paymentId)).thenReturn(payment);

        mockMvc.perform(get("/v1/payments/{id}", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.amount").value(4990))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.externalReference").value("order-1"))
                .andExpect(jsonPath("$.pix.copyPaste").value(payment.getPixCopyPaste()));
    }

    @Test
    void getReturns404WhenPaymentDoesNotBelongToMerchant() throws Exception {
        when(service.get(merchantId, paymentId)).thenThrow(new PaymentNotFoundException());

        mockMvc.perform(get("/v1/payments/{id}", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Payment not found"));
    }

    @Test
    void getReturns400WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/v1/payments/{id}", "abc").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    // ---------- GET /v1/payments ----------

    @Test
    void listReturnsEnvelopeWithDataAndHasMore() throws Exception {
        Payment first = newPayment();
        Payment second = newPayment();
        when(service.list(eq(merchantId), any())).thenReturn(new PaymentPage(List.of(first, second), true));

        mockMvc.perform(get("/v1/payments").with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$.data[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void listWithoutParametersRequestsTheFirstPageWithDefaultLimit() throws Exception {
        when(service.list(eq(merchantId), any())).thenReturn(new PaymentPage(List.of(), false));

        mockMvc.perform(get("/v1/payments").with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));

        verify(service).list(merchantId, PaymentListQuery.firstPage());
    }

    @Test
    void listPassesPaginationAndFiltersToTheService() throws Exception {
        UUID cursor = UUID.randomUUID();
        when(service.list(eq(merchantId), any())).thenReturn(new PaymentPage(List.of(), false));

        mockMvc.perform(get("/v1/payments")
                        .param("limit", "25")
                        .param("startingAfter", cursor.toString())
                        .param("status", "PAID")
                        .param("externalReference", "order-1")
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isOk());

        verify(service).list(merchantId, new PaymentListQuery(25, cursor, PaymentStatus.PAID, "order-1"));
    }

    @Test
    void listReturns400WhenLimitIsOutOfRange() throws Exception {
        mockMvc.perform(get("/v1/payments").param("limit", "101").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 100"));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WhenLimitIsNotANumber() throws Exception {
        mockMvc.perform(get("/v1/payments").param("limit", "10.5").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WhenStatusIsUnknown() throws Exception {
        mockMvc.perform(get("/v1/payments").param("status", "REFUNDED").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WhenCursorIsNotUuid() throws Exception {
        mockMvc.perform(get("/v1/payments").param("startingAfter", "abc").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listReturns400WithCodeWhenCursorIsNotOneOfTheMerchantsPayments() throws Exception {
        when(service.list(eq(merchantId), any())).thenThrow(new InvalidCursorException());

        mockMvc.perform(get("/v1/payments")
                        .param("startingAfter", UUID.randomUUID().toString())
                        .with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_starting_after"));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/payments"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    // ---------- POST /v1/payments/{id}/cancel ----------

    @Test
    void cancelReturns200WithCanceledPayment() throws Exception {
        Payment payment = newPayment();
        payment.cancel(NOW.plusSeconds(60));
        when(service.cancel(merchantId, paymentId)).thenReturn(payment);

        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.canceledAt").value("2026-11-20T12:01:00Z"));
    }

    @Test
    void cancelReturns404WhenPaymentDoesNotBelongToMerchant() throws Exception {
        when(service.cancel(merchantId, paymentId)).thenThrow(new PaymentNotFoundException());

        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelReturns409WhenPaymentIsPaid() throws Exception {
        when(service.cancel(merchantId, paymentId)).thenThrow(new PaymentNotPendingException(PaymentStatus.PAID));

        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("payment_not_pending"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void cancelReturns409WhenPaymentExpired() throws Exception {
        when(service.cancel(merchantId, paymentId)).thenThrow(new PaymentExpiredException());

        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_expired"));
    }

    @Test
    void cancelRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/v1/payments/{id}/cancel", paymentId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
