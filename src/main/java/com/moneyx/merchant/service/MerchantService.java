package com.moneyx.merchant.service;

import com.moneyx.merchant.domain.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.apikey.service.IssuedApiKey;
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

    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository repository;
    private final ApiKeyService apiKeyService;
    private final EmailVerificationService emailVerification;
    private final PasswordEncoder passwordEncoder;

    // Injeção via construtor: dependência explícita, campo final e fácil de testar com mocks.
    public MerchantService(MerchantRepository repository, ApiKeyService apiKeyService,
                           EmailVerificationService emailVerification, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.apiKeyService = apiKeyService;
        this.emailVerification = emailVerification;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cadastro com a senha do dashboard (Etapa 8b). A senha passa pela PasswordPolicy e é guardada só como hash Argon2id.
     *
     * @throws IllegalArgumentException senha fora da política (400)
     */
    @Transactional
    public MerchantRegistration register(String legalName, String document, String email, String password) {
        PasswordPolicy.validate(password, email);
        // O hash (Argon2, dezenas de ms) é calculado ANTES de qualquer escrita: a transação não segura linhas esperando.
        String hash = passwordEncoder.encode(password);
        return registerWithHash(legalName, document, email, hash);
    }

    /**
     * Cadastra o comerciante e emite sua primeira API Key na MESMA transação:
     * se a emissão da chave falhar, o comerciante também não é criado (atomicidade).
     */
    @Transactional
    public MerchantRegistration register(String legalName, String document, String email) {
        return registerWithHash(legalName, document, email, null);
    }

    /**
     * O hash entra no Merchant ANTES do primeiro save. Atribuir depois não funciona: a verificação de e-mail roda um
     * UPDATE em massa (@Modifying clearAutomatically) que limpa o contexto do JPA e "desanexa" o Merchant, e a
     * mudança se perderia em silêncio (bug pego pelo DashboardLoginIntegrationTest).
     */
    private MerchantRegistration registerWithHash(String legalName, String document, String email, String passwordHash) {
        Merchant merchant = createMerchant(legalName, document, email, passwordHash);
        IssuedApiKey apiKey = apiKeyService.issueFor(merchant.getId());
        // Link de verificação do e-mail (sai depois do commit). Sem e-mail verificado, só chaves mx_test_.
        emailVerification.requestFor(merchant);
        // Só ids: documento, e-mail e a chave NUNCA vão para o log.
        log.info("merchant.registered merchant={} apiKey={}", merchant.getId(), apiKey.apiKey().getId());
        return new MerchantRegistration(merchant, apiKey);
    }

    private Merchant createMerchant(String legalName, String document, String email, String passwordHash) {
        // A entidade valida e normaliza os dados. Assim a checagem de duplicidade
        // usa o mesmo formato salvo no banco (ex.: "123.456.789-01" -> "12345678901").
        Merchant merchant = new Merchant(legalName, document, email);
        if (passwordHash != null) {
            merchant.changePasswordHash(passwordHash, merchant.getCreatedAt());
        }

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
