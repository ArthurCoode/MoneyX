package com.moneyx.merchant.service;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.exception.DuplicateMerchantException;
import com.moneyx.merchant.exception.MerchantNotFoundException;
import com.moneyx.merchant.repository.MerchantRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Casos de uso do módulo de comerciantes.
 *
 * Responsabilidades:
 * - orquestrar entidade + repositório;
 * - aplicar regras que dependem do banco (unicidade);
 * - definir a fronteira das transações.
 *
 * Regras que dependem apenas do próprio Merchant (validação, transições de estado)
 * continuam na entidade.
 */
@Service
public class MerchantService {

    private final MerchantRepository repository;

    // Injeção via construtor: dependência explícita, campo final e fácil de testar com mocks.
    public MerchantService(MerchantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Merchant register(String legalName, String document, String email) {
        // A entidade valida e normaliza os dados. Assim a checagem de duplicidade
        // usa o mesmo formato salvo no banco (ex.: "123.456.789-01" -> "12345678901").
        Merchant merchant = new Merchant(legalName, document, email);

        if (repository.existsByDocument(merchant.getDocument())) {
            throw new DuplicateMerchantException("A merchant with this document already exists");
        }
        if (repository.existsByEmail(merchant.getEmail())) {
            throw new DuplicateMerchantException("A merchant with this email already exists");
        }

        try {
            // saveAndFlush força o INSERT agora, para capturar a violação de UNIQUE aqui dentro.
            return repository.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException e) {
            // Condição de corrida: duas requisições simultâneas podem passar pelas checagens
            // acima ao mesmo tempo. A constraint UNIQUE do banco é a garantia final.
            throw new DuplicateMerchantException("A merchant with this document or email already exists");
        }
    }

    @Transactional(readOnly = true)
    public Merchant getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException(id));
    }
}
