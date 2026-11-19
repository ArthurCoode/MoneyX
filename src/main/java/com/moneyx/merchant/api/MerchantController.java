package com.moneyx.merchant.api;

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
@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {

    private final MerchantService service;

    public MerchantController(MerchantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MerchantRegistrationResponse> create(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantRegistration registration =
                service.register(request.legalName(), request.document(), request.email());

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
    @GetMapping("/me")
    public MerchantResponse me(@AuthenticationPrincipal AuthenticatedMerchant principal) {
        return MerchantResponse.from(service.getById(principal.merchantId()));
    }
}
