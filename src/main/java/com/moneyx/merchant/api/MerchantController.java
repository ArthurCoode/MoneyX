package com.moneyx.merchant.api;

import com.moneyx.merchant.service.EmailVerificationService;
import com.moneyx.payment.service.PaymentVolumeLimitQuery;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.moneyx.payment.service.VolumeLimits;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.merchant.api.dto.CreateMerchantRequest;
import com.moneyx.merchant.api.dto.MerchantRegistrationResponse;
import com.moneyx.merchant.api.dto.MerchantResponse;
import com.moneyx.merchant.service.MerchantRegistration;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.AuthenticatedMerchant;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Endpoints HTTP do módulo de comerciantes.
 *
 * O controller é fino: converte HTTP <-> DTO e delega ao serviço.
 * Nenhuma regra de negócio fica aqui.
 *
 * Prefixo /v1: versionar a API desde o início permite mudanças incompatíveis
 * no futuro (/v2) sem quebrar integrações existentes.
 */
@Tag(name = "Merchants")
@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {

    private final MerchantService service;
    private final PaymentVolumeLimitQuery volumeLimits;
    private final EmailVerificationService emailVerification;

    public MerchantController(MerchantService service, PaymentVolumeLimitQuery volumeLimits,
                              EmailVerificationService emailVerification) {
        this.service = service;
        this.volumeLimits = volumeLimits;
        this.emailVerification = emailVerification;
    }

    /**
     * Link do e-mail de verificação (público: quem abre o link ainda não tem a chave à mão). Seguro de repetir:
     * abrir o mesmo link de novo, depois de verificado, responde 200.
     */
    @Operation(operationId = "verifyEmail", summary = "Confirmar o e-mail (link do e-mail)",
            description = "Público. O token vem no link enviado no cadastro (uso único, 24 h). Repetir o link depois de confirmado responde 200. Link inválido/substituído → 400 `invalid_verification_token`; vencido → 400 `verification_token_expired`.")
    @ApiResponse(responseCode = "200", description = "E-mail confirmado.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam(name = "token", required = false) String token) {
        emailVerification.confirm(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("emailVerified", true));
    }

    @Operation(operationId = "resendEmailVerification", summary = "Reenviar o e-mail de verificação",
            description = "Invalida os links anteriores. No máximo 1 por minuto (429 `verification_email_rate_limited` + `Retry-After`). Já verificado → 409 `email_already_verified`.")
    @ApiResponse(responseCode = "202", description = "E-mail enviado (o envio acontece em seguida).")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping("/me/email-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendEmailVerification(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        emailVerification.resend(principal.merchantId());
    }

    @Operation(operationId = "createMerchant", summary = "Cadastrar comerciante (público)",
            description = "Auto-cadastro. A resposta traz a primeira API Key **uma única vez** (`Cache-Control: no-store`). Limitado a 5 tentativas por IP (+1 a cada 2 min).")
    @ApiResponse(responseCode = "201", description = "Comerciante criado, com a primeira API Key.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping
    public ResponseEntity<MerchantRegistrationResponse> create(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantRegistration registration =
                service.register(request.legalName(), request.document(), request.email(), request.password());

        // 201 Created + header Location apontando para o recurso criado (convenção REST).
        // O comerciante só é acessível por /me, autenticado com a chave devolvida nesta resposta.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/me")
                .build()
                .toUri();

        // A resposta contém um segredo: proíbe que navegadores, proxies ou CDNs a armazenem em cache.
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(MerchantRegistrationResponse.from(registration));
    }

    /**
     * Dados do próprio comerciante autenticado.
     *
     * O ID vem da API Key (via SecurityContext), nunca da URL: não existe forma de um
     * comerciante pedir os dados de outro (proteção contra IDOR).
     */
    @Operation(operationId = "getCurrentMerchant", summary = "Consultar o próprio comerciante",
            description = "O comerciante é identificado pela API Key, nunca pela URL. Inclui `volumeLimits`: o limite acumulado de volume (centavos, por dia e por mês) que vale para ele.")
    @ApiResponse(responseCode = "200", description = "Comerciante da API Key usada.")
    @GetMapping("/me")
    public MerchantResponse me(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        VolumeLimits limits = volumeLimits.effectiveFor(principal.merchantId());
        return MerchantResponse.from(service.getById(principal.merchantId()),
                new MerchantResponse.VolumeLimits(limits.daily(), limits.monthly()));
    }
}
