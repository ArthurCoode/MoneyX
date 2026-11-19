package com.moneyx.apikey.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de API Keys.
 *
 * Este módulo conhece o comerciante apenas pelo merchantId e NUNCA importa
 * classes do módulo merchant (evita dependência circular).
 */
@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Emite uma nova chave para o comerciante.
     *
     * Propagação padrão (REQUIRED): quando chamado dentro de outra transação
     * (ex.: cadastro de comerciante), participa dela. Se algo falhar depois,
     * a chave também é desfeita.
     */
    @Transactional
    public IssuedApiKey issueFor(UUID merchantId) {
        GeneratedApiKey generated = ApiKeyGenerator.generate();
        ApiKey apiKey = repository.save(ApiKey.issue(merchantId, generated));
        return new IssuedApiKey(apiKey, generated.plainText());
    }

    /**
     * Usado na autenticação: localiza a chave ativa correspondente ao segredo recebido.
     *
     * O segredo é transformado em hash e buscado pelo índice UNIQUE de key_hash.
     * Nunca comparamos segredos em texto puro e nunca lemos o hash do banco para comparar em Java.
     *
     * Chave inexistente e chave revogada resultam no mesmo Optional.empty(): quem chama
     * não deve (e não consegue) diferenciar os dois casos, para não dar pistas a um atacante.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> findActiveByPlainText(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return Optional.empty();
        }
        return repository.findByKeyHash(ApiKeyGenerator.hash(plainText))
                .filter(ApiKey::isActive);
    }
}
