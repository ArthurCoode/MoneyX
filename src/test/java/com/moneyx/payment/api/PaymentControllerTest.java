package com.moneyx.payment.api;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.exception.IdempotencyKeyReusedException;
import com.moneyx.payment.exception.InvalidIdempotencyKeyException;
import com.moneyx.payment.exception.VolumeLimitExceededException;
import com.moneyx.payment.service.CreatePaymentCommand;
import com.moneyx.payment.service.PaymentCreation;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.moneyx.security.TestAuthentication.activeMerchant;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final String VALID_BODY = """
            {
              "amount": 4990,
              "currency": "BRL",
              "description": "Pedido 123",
              "externalReference": "order-123",
              "expiresInSeconds": 1800
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService service;

    @MockitoBean
    private ApiKeyService apiKeyService;   // dependência do filtro de API Key

    @MockitoBean
    private MerchantService merchantService; // dependência do filtro de API Key

    private final UUID merchantId = UUID.randomUUID();

    private Payment payment() {
        return Payment.create(merchantId, 4990, PaymentCurrency.BRL, "Pedido 123", "order-123", null,
                Instant.parse("2026-11-19T12:00:00Z"));
    }

    private ResultActions postPayment(String idempotencyKey, String body) throws Exception {
        var request = post("/v1/payments")
                .with(activeMerchant(merchantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    // ---------- sucesso ----------

    @Test
    void createReturns201WithPaymentAndLocation() throws Exception {
        Payment payment = payment();
        CreatePaymentCommand expected =
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, "Pedido 123", "order-123", 1800L);
        when(service.create(merchantId, "key-1", expected)).thenReturn(new PaymentCreation(payment, false));

        postPayment("key-1", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/v1/payments/" + payment.getId())))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(4990))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.description").value("Pedido 123"))
                .andExpect(jsonPath("$.externalReference").value("order-123"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-19T12:30:00Z"))
                .andExpect(jsonPath("$.pix.txid").value(payment.getPixTxid()))
                .andExpect(jsonPath("$.pix.copyPaste").value(payment.getPixCopyPaste()))
                .andExpect(jsonPath("$.merchantId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void replayedRequestIsFlaggedInHeader() throws Exception {
        when(service.create(eq(merchantId), eq("key-1"), any())).thenReturn(new PaymentCreation(payment(), true));

        postPayment("key-1", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void optionalFieldsMayBeOmitted() throws Exception {
        when(service.create(eq(merchantId), eq("key-1"), any())).thenReturn(new PaymentCreation(payment(), false));

        postPayment("key-1", """
                {"amount": 4990, "currency": "BRL"}
                """)
                .andExpect(status().isCreated());

        verify(service).create(merchantId, "key-1",
                new CreatePaymentCommand(4990, PaymentCurrency.BRL, null, null, null));
    }

    // ---------- Idempotency-Key ----------

    @Test
    void returns400WhenIdempotencyKeyIsMissing() throws Exception {
        postPayment(null, VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void returns400WithCodeWhenIdempotencyKeyIsInvalid() throws Exception {
        when(service.create(eq(merchantId), anyString(), any())).thenThrow(new InvalidIdempotencyKeyException());

        postPayment("bad key", VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_idempotency_key"));
    }

    @Test
    void returns422WithCodeWhenKeyIsReusedWithDifferentBody() throws Exception {
        when(service.create(eq(merchantId), eq("key-1"), any())).thenThrow(new IdempotencyKeyReusedException());

        postPayment("key-1", VALID_BODY)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));
    }

    @Test
    void returns422WithCodeAndPeriodDetailsWhenVolumeLimitIsExceeded() throws Exception {
        when(service.create(eq(merchantId), eq("key-1"), any())).thenThrow(new VolumeLimitExceededException(
                VolumeLimitExceededException.Period.DAILY, 20_000_000, 1_500, Instant.parse("2026-11-20T03:00:00Z")));

        postPayment("key-1", VALID_BODY)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("volume_limit_exceeded"))
                .andExpect(jsonPath("$.period").value("daily"))
                .andExpect(jsonPath("$.limit").value(20_000_000))
                .andExpect(jsonPath("$.remaining").value(1_500))
                .andExpect(jsonPath("$.resetsAt").value("2026-11-20T03:00:00Z"))
                .andExpect(jsonPath("$.detail").value("This payment would exceed the daily payment volume limit"));
    }

    // ---------- validação do corpo (nunca chega ao serviço) ----------

    /**
     * Risco crítico: alguns conversores JSON aceitam 49.90 num campo inteiro e TRUNCAM para 49,
     * criando uma cobrança de R$ 0,49 em vez de R$ 49,90, sem nenhum erro.
     */
    @ParameterizedTest
    @ValueSource(strings = {"49.90", "4990.0", "4990.5", "1e3"})
    void decimalAmountIsRejectedInsteadOfTruncated(String amount) throws Exception {
        postPayment("key-1", """
                {"amount": %s, "currency": "BRL"}
                """.formatted(amount))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"currency\": \"BRL\"}",                                           // amount ausente
            "{\"amount\": 99, \"currency\": \"BRL\"}",                           // abaixo de R$ 1,00
            "{\"amount\": 10000001, \"currency\": \"BRL\"}",                     // acima de R$ 100.000,00
            "{\"amount\": -4990, \"currency\": \"BRL\"}",                        // negativo
            "{\"amount\": 4990}",                                                // currency ausente
            "{\"amount\": 4990, \"currency\": \"USD\"}",                         // moeda não suportada
            "{\"amount\": 4990, \"currency\": \"brl\"}",                         // case errado
            "{\"amount\": 4990, \"currency\": \"BRL\", \"expiresInSeconds\": 299}",
            "{\"amount\": 4990, \"currency\": \"BRL\", \"expiresInSeconds\": 86401}"
    })
    void invalidBodyIsRejectedWithFieldErrors(String body) throws Exception {
        postPayment("key-1", body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors").isArray());

        verifyNoInteractions(service);
    }

    @Test
    void textsLongerThanAllowedAreRejected() throws Exception {
        postPayment("key-1", """
                {"amount": 4990, "currency": "BRL", "description": "%s", "externalReference": "%s"}
                """.formatted("x".repeat(256), "y".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        verifyNoInteractions(service);
    }

    // ---------- segurança ----------

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
