package com.moneyx.apikey.repository;

import com.moneyx.apikey.domain.ApiKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Usado na autenticação: a chave recebida é transformada em hash e buscada por ele. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /** Lista as chaves de um comerciante (sempre filtrado por merchantId: isolamento multi-tenant). */
    List<ApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /**
     * Busca uma chave pelo id E pelo dono, na mesma consulta.
     * Chave de outro comerciante simplesmente "não existe" para quem pergunta.
     * Nunca buscar só pelo id e comparar o dono depois.
     */
    Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);

    long countByMerchantIdAndRevokedAtIsNull(UUID merchantId);

    /**
     * Tranca (SELECT ... FOR UPDATE) as chaves ativas do comerciante até o fim da transação.
     *
     * Serializa operações concorrentes do MESMO comerciante (criar/revogar), impedindo que
     * duas requisições simultâneas furem as regras "máximo de chaves ativas" e "não revogar
     * a última chave". Comerciantes diferentes não se bloqueiam.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ApiKey k where k.merchantId = :merchantId and k.revokedAt is null")
    List<ApiKey> lockActiveByMerchantId(UUID merchantId);

    /**
     * O e-mail do comerciante foi verificado? SQL nativo sobre merchants: o módulo apikey não importa o merchant
     * (mesmo padrão do payment).
     */
    @Query(value = "SELECT email_verified_at IS NOT NULL FROM merchants WHERE id = :merchantId", nativeQuery = true)
    Optional<Boolean> isMerchantEmailVerified(UUID merchantId);
}
