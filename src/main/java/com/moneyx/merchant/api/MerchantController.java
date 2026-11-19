package com.moneyx.merchant.api;

import com.moneyx.merchant.api.dto.CreateMerchantRequest;
import com.moneyx.merchant.api.dto.MerchantRegistrationResponse;
import com.moneyx.merchant.api.dto.MerchantResponse;
import com.moneyx.merchant.service.MerchantRegistration;
import com.moneyx.merchant.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

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

        // 201 Created + header Location apontando para o novo recurso (convenção REST).
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(registration.merchant().getId())
                .toUri();

        // A resposta contém um segredo: proíbe que navegadores, proxies ou CDNs a armazenem em cache.
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(MerchantRegistrationResponse.from(registration));
    }

    @GetMapping("/{id}")
    public MerchantResponse getById(@PathVariable UUID id) {
        return MerchantResponse.from(service.getById(id));
    }
}
