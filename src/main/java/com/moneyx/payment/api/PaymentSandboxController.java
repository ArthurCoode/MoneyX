package com.moneyx.payment.api;

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
@RestController
@RequestMapping("/v1/sandbox/payments")
public class PaymentSandboxController {

    private final PaymentService service;

    public PaymentSandboxController(PaymentService service) {
        this.service = service;
    }

    /** Simula o pagador pagando o Pix. 200 com o pagamento já PAID. */
    @PostMapping("/{id}/pay")
    public PaymentView pay(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        return PaymentView.from(service.simulatePixPayment(principal.merchantId(), id));
    }
}
