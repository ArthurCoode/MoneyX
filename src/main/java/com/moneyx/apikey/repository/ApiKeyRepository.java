package com.moneyx.apikey.repository;

import com.moneyx.apikey.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Usado na autenticação: a chave recebida é transformada em hash e buscada por ele. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /** Lista as chaves de um comerciante (sempre filtrado por merchantId: isolamento multi-tenant). */
    List<ApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
