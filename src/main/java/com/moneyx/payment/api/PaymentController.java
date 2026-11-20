package com.moneyx.payment.api;

import com.moneyx.payment.api.dto.CreatePaymentRequest;
import com.moneyx.payment.api.dto.PaymentListResponse;
import com.moneyx.payment.api.dto.PaymentResponse;
import com.moneyx.payment.domain.PaymentStatus;
import com.moneyx.payment.service.PaymentCreation;
import com.moneyx.payment.service.PaymentListQuery;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.security.AuthenticatedMerchant;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    /** Indica que a resposta é de uma requisição repetida (mesmo nome usado pelo Stripe). */
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /**
     * Cria um pagamento Pix.
     *
     * O header Idempotency-Key é OBRIGATÓRIO (ausente -> 400). Repetir a requisição com a mesma
     * chave e o mesmo corpo devolve o pagamento original, com o header Idempotent-Replayed: true.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal AuthenticatedMerchant principal,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentCreation creation = service.create(principal.merchantId(), idempotencyKey, request.toCommand());

        // Aponta para GET /v1/payments/{id}.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creation.payment().getId())
                .toUri();

        return ResponseEntity.created(location)
                .header(IDEMPOTENT_REPLAYED_HEADER, Boolean.toString(creation.replayed()))
                .body(PaymentResponse.from(creation.payment()));
    }

    /** Consulta um pagamento. 404 se não existir ou for de outro comerciante. */
    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return PaymentResponse.from(service.get(principal.merchantId(), id));
    }

    /**
     * Lista os pagamentos do comerciante, mais recentes primeiro.
     *
     * Paginação por cursor: ?limit=10&startingAfter=<id do último item da página anterior>.
     * Filtros opcionais: status e externalReference.
     */
    @GetMapping
    public PaymentListResponse list(
            @AuthenticationPrincipal AuthenticatedMerchant principal,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) UUID startingAfter,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String externalReference) {

        PaymentListQuery query = new PaymentListQuery(limit, startingAfter, status, externalReference);
        return PaymentListResponse.from(service.list(principal.merchantId(), query));
    }

    /**
     * Cancela um pagamento PENDING. Ação explícita (POST), não DELETE: o pagamento continua existindo,
     * apenas muda de estado. Idempotente: cancelar de novo um pagamento CANCELED responde 200.
     */
    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return PaymentResponse.from(service.cancel(principal.merchantId(), id));
    }
}
