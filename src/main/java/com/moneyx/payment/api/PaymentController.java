package com.moneyx.payment.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.payment.api.dto.CreatePaymentRequest;
import com.moneyx.payment.api.dto.PaymentListResponse;
import com.moneyx.payment.service.PaymentView;
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

@Tag(name = "Payments")
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
    @Operation(operationId = "createPayment", summary = "Criar pagamento Pix",
            description = "Cria uma cobrança Pix com BR Code (`pix.copyPaste`). Exige `Idempotency-Key`: repetir a mesma chave e o mesmo corpo devolve o pagamento original.")
    @ApiResponse(responseCode = "201", description = "Pagamento criado (ou devolvido, se for repetição).", headers = @Header(name = "Idempotent-Replayed", description = "true quando a resposta é de uma repetição.", schema = @io.swagger.v3.oas.annotations.media.Schema(types = {"boolean"})))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableContent")
    @PostMapping
    public ResponseEntity<PaymentView> create(
            @AuthenticationPrincipal AuthenticatedMerchant principal,
            @Parameter(description = "Obrigatório. 1 a 255 caracteres ASCII visíveis, válido por 24 h. Gere uma chave nova (ex.: UUID) por pagamento.") @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentCreation creation = service.create(principal.merchantId(), idempotencyKey, request.toCommand());

        // Aponta para GET /v1/payments/{id}.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creation.payment().getId())
                .toUri();

        return ResponseEntity.created(location)
                .header(IDEMPOTENT_REPLAYED_HEADER, Boolean.toString(creation.replayed()))
                .body(PaymentView.from(creation.payment()));
    }

    /** Consulta um pagamento. 404 se não existir ou for de outro comerciante. */
    @Operation(operationId = "getPayment", summary = "Consultar pagamento",
            description = "Estado atual do pagamento.")
    @ApiResponse(responseCode = "200", description = "Pagamento.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}")
    public PaymentView get(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return PaymentView.from(service.get(principal.merchantId(), id));
    }

    /**
     * Lista os pagamentos do comerciante, mais recentes primeiro.
     *
     * Paginação por cursor: ?limit=10&startingAfter=<id do último item da página anterior>.
     * Filtros opcionais: status e externalReference.
     */
    @Operation(operationId = "listPayments", summary = "Listar pagamentos",
            description = "Mais recentes primeiro, paginação por cursor. Filtros: `status`, `externalReference`.")
    @ApiResponse(responseCode = "200", description = "Página de pagamentos.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
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
    @Operation(operationId = "cancelPayment", summary = "Cancelar pagamento",
            description = "Só PENDING dentro do prazo. Idempotente: cancelar de novo responde 200. PAID/EXPIRED → 409 `payment_not_pending`; prazo vencido → 409 `payment_expired`.")
    @ApiResponse(responseCode = "200", description = "Pagamento cancelado.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/{id}/cancel")
    public PaymentView cancel(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return PaymentView.from(service.cancel(principal.merchantId(), id));
    }
}
