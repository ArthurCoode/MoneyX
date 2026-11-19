package com.moneyx.apikey.service;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.domain.ApiKeyGenerator;
import com.moneyx.apikey.domain.ApiKeyGenerator.GeneratedApiKey;
import com.moneyx.apikey.exception.ApiKeyLimitExceededException;
import com.moneyx.apikey.exception.ApiKeyNotFoundException;
import com.moneyx.apikey.exception.LastActiveApiKeyException;
import com.moneyx.apikey.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de API Keys.
 *
 * Este módulo conhece o comerciante apenas pelo merchantId e NUNCA importa
 * classes do módulo merchant (evita dependência circular).
 *
 * Regras:
 * - no máximo MAX_ACTIVE_KEYS_PER_MERCHANT chaves ativas por comerciante;
 * - a última chave ativa não pode ser revogada;
 * - revogar uma chave já revogada não é erro (idempotente).
 *
 * As regras que dependem de "quantas chaves ativas existem" trancam primeiro as chaves
 * ativas do comerciante (SELECT ... FOR UPDATE). Sem isso, duas requisições simultâneas
 * poderiam ler a mesma contagem e ambas furarem a regra.
 */
@Service
public class ApiKeyService {

    public static final int MAX_ACTIVE_KEYS_PER_MERCHANT = 5;

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
        repository.lockActiveByMerchantId(merchantId);

        // A contagem é uma NOVA consulta, executada depois de obter o lock: no PostgreSQL
        // (READ COMMITTED) ela enxerga inclusive chaves criadas por quem estava na nossa frente na fila.
        if (repository.countByMerchantIdAndRevokedAtIsNull(merchantId) >= MAX_ACTIVE_KEYS_PER_MERCHANT) {
            throw new ApiKeyLimitExceededException(MAX_ACTIVE_KEYS_PER_MERCHANT);
        }

        GeneratedApiKey generated = ApiKeyGenerator.generate();
        ApiKey apiKey = repository.save(ApiKey.issue(merchantId, generated));
        return new IssuedApiKey(apiKey, generated.plainText());
    }

    /** Todas as chaves do comerciante (ativas e revogadas), da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<ApiKey> listFor(UUID merchantId) {
        return repository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Revoga uma chave do comerciante.
     *
     * @throws ApiKeyNotFoundException   se a chave não existir ou for de outro comerciante
     * @throws LastActiveApiKeyException se for a última chave ativa
     */
    @Transactional
    public void revoke(UUID merchantId, UUID apiKeyId) {
        // Lock ANTES de ler a chave: se outra transação acabou de revogá-la, ao obter o lock
        // já enxergamos o estado atualizado (e não uma cópia antiga).
        List<ApiKey> activeKeys = repository.lockActiveByMerchantId(merchantId);

        ApiKey apiKey = repository.findByIdAndMerchantId(apiKeyId, merchantId)
                .orElseThrow(ApiKeyNotFoundException::new);

        if (!apiKey.isActive()) {
            return; // Idempotente: o estado desejado ("revogada") já foi atingido.
        }
        if (activeKeys.size() <= 1) {
            throw new LastActiveApiKeyException();
        }

        // A entidade está gerenciada pelo JPA: a alteração é gravada no commit (sem save explícito).
        apiKey.revoke();
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
