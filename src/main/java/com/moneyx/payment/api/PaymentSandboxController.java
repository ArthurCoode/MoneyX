package com.moneyx.payment.api;

import com.moneyx.payment.exception.SandboxRequiresTestKeyException;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.payment.service.PaymentView;
import com.moneyx.payment.service.PaymentService;
import com.moneyx.security.AuthenticatedMerchant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints de SANDBOX: simulam eventos que, em produção, acontecem fora do MoneyX.
 *
 * Hoje todas as chaves são de teste (mx_test_). Na Fase 7, quando existirem chaves mx_live_,
 * estas rotas devem ser bloqueadas para elas (o pagamento real é confirmado pelo PSP).
 */
@Tag(name = "Sandbox")
@RestController
@RequestMapping("/v1/sandbox/payments")
public class PaymentSandboxController {

    private final PaymentService service;

    public PaymentSandboxController(PaymentService service) {
        this.service = service;
    }

    /** Simula o pagador pagando o Pix. 200 com o pagamento já PAID. */
    @Operation(operationId = "simulatePixPayment", summary = "Simular o pagamento do Pix",
            description = "SANDBOX: simula o pagador pagando o Pix. Só para cobranças do simulador local; uma cobrança de PSP real (ex.: Mercado Pago) responde 409 `sandbox_payment_not_supported`. Só com chave de teste (`mx_test_`): chave `mx_live_` → 403 `sandbox_requires_test_key`.")
    @ApiResponse(responseCode = "200", description = "Pagamento PAID.")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/{id}/pay")
    public PaymentView pay(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        if (principal.live()) {
            throw new SandboxRequiresTestKeyException();
        }
        return PaymentView.from(service.simulatePixPayment(principal.merchantId(), id));
    }
}
