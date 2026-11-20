package com.moneyx.webhook.repository;

import com.moneyx.webhook.domain.WebhookEndpointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Linha do tempo: só se grava (save) e se lê. Não há atualização nem remoção. */
public interface WebhookEndpointHistoryRepository extends JpaRepository<WebhookEndpointHistory, UUID> {

    /** Do mais antigo para o mais recente (ordem natural de uma linha do tempo); empate desempatado pelo id. */
    List<WebhookEndpointHistory> findByEndpointIdAndMerchantIdOrderByOccurredAtAscIdAsc(UUID endpointId, UUID merchantId);
}
