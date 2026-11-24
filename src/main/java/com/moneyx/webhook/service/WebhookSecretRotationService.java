package com.moneyx.webhook.service;

import com.moneyx.shared.crypto.SecretCipher;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Recifra com a chave ATIVA os segredos de assinatura que ainda estão com outra chave (a anterior, ou o formato
 * antigo sem prefixo). Roda em lotes, cada um numa transação curta.
 *
 * Rotação, do ponto de vista do operador:
 * 1. mover o valor atual de MONEYX_WEBHOOK_ENCRYPTION_KEY para MONEYX_WEBHOOK_ENCRYPTION_KEY_PREVIOUS;
 * 2. gerar uma chave nova em MONEYX_WEBHOOK_ENCRYPTION_KEY e reiniciar a aplicação;
 * 3. esperar o log "webhook_secret.reencryption ... remaining=0" (o job roda a cada hora);
 * 4. apagar MONEYX_WEBHOOK_ENCRYPTION_KEY_PREVIOUS e reiniciar.
 *
 * Durante a rotação nada para: a entrega decifra com qualquer uma das duas chaves.
 */
@Service
public class WebhookSecretRotationService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecretRotationService.class);

    static final int BATCH_SIZE = 100;
    /** Menor UUID na ordem do PostgreSQL: ponto de partida do cursor. */
    private static final UUID START = new UUID(0L, 0L);

    private final WebhookEndpointRepository repository;
    private final SecretCipher cipher;
    private final TransactionTemplate transaction;

    public WebhookSecretRotationService(WebhookEndpointRepository repository,
                                        SecretCipher cipher,
                                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.cipher = cipher;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Resultado de uma execução. remaining = 0 significa que a chave anterior já pode ser removida. */
    public record Result(int reencrypted, int failed, long remaining) {
    }

    public Result reencryptAll() {
        return reencryptAll(BATCH_SIZE);
    }

    Result reencryptAll(int batchSize) {
        String pattern = cipher.activePrefix() + "%";
        int reencrypted = 0;
        int failed = 0;
        UUID cursor = START;

        while (true) {
            UUID after = cursor;
            BatchResult batch = transaction.execute(status -> reencryptBatch(pattern, after, batchSize));
            if (batch == null || batch.processed() == 0) {
                break;
            }
            reencrypted += batch.reencrypted();
            failed += batch.failed();
            cursor = batch.lastId();
        }

        long remaining = repository.countNotEncryptedWith(pattern);
        if (reencrypted > 0 || failed > 0 || remaining > 0) {
            // Só contagens e ids: nunca segredo nem chave.
            log.info("webhook_secret.reencryption activeKeyId={} reencrypted={} failed={} remaining={}",
                    cipher.activeKeyId(), reencrypted, failed, remaining);
        }
        return new Result(reencrypted, failed, remaining);
    }

    private BatchResult reencryptBatch(String pattern, UUID after, int batchSize) {
        List<WebhookEndpoint> endpoints = repository.lockNotEncryptedWith(pattern, after, batchSize);
        int reencrypted = 0;
        int failed = 0;
        for (WebhookEndpoint endpoint : endpoints) {
            try {
                // AAD = id do endpoint, o mesmo da cifragem original.
                endpoint.replaceSecretCiphertext(
                        cipher.reencrypt(endpoint.getSecretCiphertext(), endpoint.getId().toString()));
                reencrypted++;
            } catch (IllegalStateException e) {
                // Não decifra com nenhuma chave configurada: fica como está (o cursor passa por ele) e é contado
                // em "remaining". A mensagem não contém segredo.
                failed++;
                log.warn("webhook_secret.reencryption_failed endpointId={} reason={}", endpoint.getId(), e.getMessage());
            }
        }
        UUID lastId = endpoints.isEmpty() ? after : endpoints.getLast().getId();
        return new BatchResult(endpoints.size(), reencrypted, failed, lastId);
    }

    private record BatchResult(int processed, int reencrypted, int failed, UUID lastId) {
    }
}
