package com.moneyx.apikey.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.moneyx.apikey.api.dto.ApiKeyListResponse;
import com.moneyx.apikey.api.dto.ApiKeyResponse;
import com.moneyx.apikey.api.dto.CreatedApiKeyResponse;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.security.AuthenticatedMerchant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gerenciamento das API Keys do comerciante autenticado.
 *
 * Todas as operações usam o merchantId do AuthenticatedMerchant (vindo da chave usada
 * na requisição), nunca um id informado pelo cliente.
 */
@Tag(name = "API Keys")
@RestController
@RequestMapping("/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    /** Cria uma nova chave. O segredo aparece SOMENTE nesta resposta. */
    @Operation(operationId = "createApiKey", summary = "Criar API Key",
            description = "O segredo aparece **somente** nesta resposta. Máximo de 5 chaves ativas.")
    @ApiResponse(responseCode = "201", description = "Chave criada.")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @PostMapping
    public ResponseEntity<CreatedApiKeyResponse> create(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        CreatedApiKeyResponse body = CreatedApiKeyResponse.from(service.issueFor(principal.merchantId()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @Operation(operationId = "listApiKeys", summary = "Listar API Keys",
            description = "Ativas e revogadas, sem o segredo (apenas o prefixo).")
    @ApiResponse(responseCode = "200", description = "Chaves do comerciante.")
    @GetMapping
    public ApiKeyListResponse list(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        return new ApiKeyListResponse(service.listFor(principal.merchantId()).stream()
                .map(ApiKeyResponse::from)
                .toList());
    }

    /** Revoga a chave. Idempotente: revogar de novo também responde 204. */
    @Operation(operationId = "revokeApiKey", summary = "Revogar API Key",
            description = "Idempotente. A última chave ativa não pode ser revogada (`last_active_api_key`).")
    @ApiResponse(responseCode = "204", description = "Revogada (ou já estava).")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthenticatedMerchant principal, @PathVariable UUID id) {
        service.revoke(principal.merchantId(), id);
    }
}
