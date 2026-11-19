package com.moneyx.payment.api;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.exception.PaymentExpiredException;
import com.moneyx.payment.exception.PaymentNotFoundException;
import com.moneyx.payment.exception.PaymentNotPendingException;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.moneyx.security.TestAuthentication.activeMerchant;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentSandboxController.class)
@Import(SecurityConfig.class)
class PaymentSandboxControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

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

    @Test
    void payReturns200WithPaidPayment() throws Exception {
        Payment payment = Payment.create(merchantId, 4990, PaymentCurrency.BRL, null, null, null, NOW);
        payment.markPaid(NOW.plusSeconds(60));
        when(service.simulatePixPayment(merchantId, paymentId)).thenReturn(payment);

        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").value("2026-11-19T12:01:00Z"))
                .andExpect(jsonPath("$.pix.txid").value(payment.getPixTxid()))
                .andExpect(jsonPath("$.pix.copyPaste").value(payment.getPixCopyPaste()));
    }

    @Test
    void payReturns404WhenPaymentDoesNotBelongToMerchant() throws Exception {
        when(service.simulatePixPayment(merchantId, paymentId)).thenThrow(new PaymentNotFoundException());

        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Payment not found"));
    }

    @Test
    void payReturns409WhenPaymentIsNotPending() throws Exception {
        when(service.simulatePixPayment(merchantId, paymentId))
                .thenThrow(new PaymentNotPendingException(PaymentStatus.PAID));

        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("payment_not_pending"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void payReturns409WhenPaymentExpired() throws Exception {
        when(service.simulatePixPayment(merchantId, paymentId)).thenThrow(new PaymentExpiredException());

        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_expired"));
    }

    @Test
    void payReturns409InsteadOf500OnOptimisticLockConflict() throws Exception {
        when(service.simulatePixPayment(merchantId, paymentId))
                .thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, paymentId));

        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId).with(activeMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("concurrent_update"));
    }

    @Test
    void payReturns400WhenIdIsNotUuid() throws Exception {
        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", "abc").with(activeMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void payRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/v1/sandbox/payments/{id}/pay", paymentId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
