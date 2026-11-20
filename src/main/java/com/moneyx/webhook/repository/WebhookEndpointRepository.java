package com.moneyx.webhook.repository;

import com.moneyx.webhook.domain.WebhookEndpoint;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    /** Busca por id E dono (inclusive removidos): de outro comerciante simplesmente "não existe". */
    Optional<WebhookEndpoint> findByIdAndMerchantId(UUID id, UUID merchantId);

    /** Mesma busca trancando a linha (remoção concorrente do mesmo endpoint grava a linha do tempo uma vez só). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from WebhookEndpoint e where e.id = :id and e.merchantId = :merchantId")
    Optional<WebhookEndpoint> lockByIdAndMerchantId(UUID id, UUID merchantId);

    List<WebhookEndpoint> findByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID merchantId);

    long countByMerchantIdAndDeletedAtIsNull(UUID merchantId);

    /**
     * Tranca a LINHA DO COMERCIANTE (SELECT ... FOR UPDATE) até o fim da transação: é o "cadeado" da regra
     * "no máximo N endpoints ativos".
     *
     * Por que não trancar os endpoints, como nas API Keys? Lá o comerciante sempre tem ao menos uma chave.
     * Aqui ele pode ter ZERO endpoints: não haveria linha para trancar, e cadastros simultâneos passariam
     * todos pela contagem. A linha do comerciante sempre existe (é o "pai" de todos os endpoints).
     *
     * SQL nativo sobre a tabela merchants: o módulo webhook não importa o módulo merchant (a ligação entre
     * eles já existe no banco, pela FK). Comerciantes diferentes não se bloqueiam.
     *
     * @return o id, se o comerciante existir
     */
    @Query(value = "SELECT CAST(id AS VARCHAR) FROM merchants WHERE id = :merchantId FOR UPDATE", nativeQuery = true)
    Optional<String> lockMerchant(UUID merchantId);
}
