package com.moneyx.merchant.api;

import com.moneyx.merchant.api.dto.ConfirmPasswordResetRequest;
import com.moneyx.merchant.api.dto.PasswordResetRequest;
import com.moneyx.merchant.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Definir/redefinir a senha do dashboard por um link enviado ao e-mail (Etapa 8b). Público. */
@Tag(name = "Merchants")
@RestController
@RequestMapping("/v1/merchants/password-reset")
public class PasswordResetController {

    private final PasswordResetService resets;

    public PasswordResetController(PasswordResetService resets) {
        this.resets = resets;
    }

    @Operation(operationId = "requestPasswordReset", summary = "Pedir o link de definir/redefinir senha",
            description = "Público. Responde 202 SEMPRE (exista a conta ou não), para não revelar quem tem conta. O link vale 1 h e substitui os anteriores. "
                    + "Muitos pedidos (por IP ou por e-mail) → 429 `rate_limit_exceeded` + `Retry-After`.")
    @ApiResponse(responseCode = "202", description = "Se a conta existir, o e-mail foi enviado.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        resets.request(request.email(), http.getRemoteAddr());
    }

    @Operation(operationId = "confirmPasswordReset", summary = "Salvar a nova senha (link do e-mail)",
            description = "Público. Troca a senha, encerra TODAS as sessões abertas e confirma o e-mail. Link inválido/usado → 400 `invalid_reset_token`; vencido → 400 `reset_token_expired`.")
    @ApiResponse(responseCode = "204", description = "Senha trocada.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        resets.confirm(request.token(), request.newPassword());
    }
}
