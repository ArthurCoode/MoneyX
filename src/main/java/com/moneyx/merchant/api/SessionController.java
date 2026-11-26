package com.moneyx.merchant.api;

import com.moneyx.merchant.api.dto.CreateSessionRequest;
import com.moneyx.merchant.api.dto.SessionResponse;
import com.moneyx.merchant.domain.MerchantSession;
import com.moneyx.merchant.service.SessionService;
import com.moneyx.security.AuthenticatedMerchant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessões do dashboard (Etapa 8b). Quem chama é o BFF do frontend (servidor do Next.js), nunca o navegador direto: o
 * token devolvido fica num cookie HttpOnly do BFF e volta à API no header Authorization (Bearer mx_sess_...).
 */
@Tag(name = "Sessions")
@RestController
@RequestMapping("/v1/sessions")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @Operation(operationId = "createSession", summary = "Entrar no dashboard (e-mail + senha)",
            description = "Público. Devolve o token da sessão (`mx_sess_...`) UMA vez (`Cache-Control: no-store`). Vale 6 h sem uso ou 24 h no total. "
                    + "E-mail inexistente e senha errada dão a mesma resposta: 401 `invalid_credentials`. Muitas tentativas (por IP ou por e-mail) → 429 `rate_limit_exceeded` + `Retry-After`.")
    @ApiResponse(responseCode = "201", description = "Sessão criada.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
    @PostMapping
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request,
                                                  HttpServletRequest http) {
        MerchantSession.Started started = sessions.login(request.email(), request.password(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(SessionResponse.from(started));
    }

    @Operation(operationId = "deleteCurrentSession", summary = "Sair (encerrar a sessão atual)",
            description = "Revoga a sessão usada na requisição. Idempotente. Com uma API Key não há sessão: responde 204 sem efeito.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada.")
    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCurrent(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        if (principal.sessionId() != null) {
            sessions.logout(principal.merchantId(), principal.sessionId());
        }
    }
}
