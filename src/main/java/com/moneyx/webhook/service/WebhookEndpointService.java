package com.moneyx.webhook.service;

import com.moneyx.event.domain.EventType;
import com.moneyx.shared.crypto.SecretCipher;
import com.moneyx.webhook.domain.WebhookEndpoint;
import com.moneyx.webhook.domain.WebhookEndpointAction;
import com.moneyx.webhook.domain.WebhookEndpointHistory;
import com.moneyx.webhook.domain.WebhookSecretGenerator;
import com.moneyx.webhook.domain.WebhookSecretGenerator.GeneratedSecret;
import com.moneyx.webhook.domain.WebhookUrlValidator;
import com.moneyx.webhook.exception.InvalidWebhookUrlException;
import com.moneyx.webhook.exception.WebhookEndpointLimitExceededException;
import com.moneyx.webhook.exception.WebhookEndpointNotFoundException;
import com.moneyx.webhook.repository.WebhookEndpointHistoryRepository;
import com.moneyx.webhook.repository.WebhookEndpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Cadastro de endpoints de webhook, com linha do tempo (auditoria) de cada mudança.
 *
 * Toda mudança de estado grava, NA MESMA TRANSAÇÃO, um registro em webhook_endpoint_history:
 * não existe mudança sem registro, nem registro de algo que não aconteceu.
 */
@Service
public class WebhookEndpointService {

    public static final int MAX_ACTIVE_ENDPOINTS_PER_MERCHANT = 5;

    private final WebhookEndpointRepository repository;
    private final WebhookEndpointHistoryRepository historyRepository;
    private final SecretCipher cipher;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final boolean allowLocalUrls;

    public WebhookEndpointService(WebhookEndpointRepository repository,
                                  WebhookEndpointHistoryRepository historyRepository,
                                  SecretCipher cipher,
                                  JsonMapper jsonMapper,
                                  Clock clock,
                                  @Value("${moneyx.webhooks.allow-local-urls:false}") boolean allowLocalUrls) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.cipher = cipher;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.allowLocalUrls = allowLocalUrls;
    }

    /**
     * Cadastra um endpoint e devolve o segredo de assinatura em texto puro (a ÚNICA vez em que ele sai do MoneyX).
     *
     * @param apiKeyId      chave usada na requisição (vai para a linha do tempo)
     * @param enabledEvents valores públicos dos tipos; null = todos
     * @throws InvalidWebhookUrlException           URL fora das regras
     * @throws IllegalArgumentException             lista de eventos vazia ou com tipo desconhecido
     * @throws WebhookEndpointLimitExceededException já há o máximo de endpoints ativos
     */
    @Transactional
    public CreatedWebhookEndpoint create(UUID merchantId, UUID apiKeyId, String url, List<String> enabledEvents) {
        String validUrl;
        try {
            validUrl = WebhookUrlValidator.validate(url, allowLocalUrls);
        } catch (IllegalArgumentException e) {
            throw new InvalidWebhookUrlException(e.getMessage());
        }
        List<EventType> types = parseEventTypes(enabledEvents);

        // Cadeado por comerciante ANTES de contar (funciona mesmo com zero endpoints; ver lockMerchant).
        repository.lockMerchant(merchantId)
                .orElseThrow(() -> new IllegalStateException("Authenticated merchant not found"));
        if (repository.countByMerchantIdAndDeletedAtIsNull(merchantId) >= MAX_ACTIVE_ENDPOINTS_PER_MERCHANT) {
            throw new WebhookEndpointLimitExceededException(MAX_ACTIVE_ENDPOINTS_PER_MERCHANT);
        }

        Instant now = now();
        UUID id = UUID.randomUUID();
        GeneratedSecret secret = WebhookSecretGenerator.generate();
        // O id do endpoint é o dado associado (AAD): este texto cifrado só decifra para ESTE endpoint.
        String ciphertext = cipher.encrypt(secret.plainText(), id.toString());

        WebhookEndpoint endpoint = repository.save(WebhookEndpoint.create(
                id, merchantId, validUrl, types, ciphertext, secret.displayPrefix(), now));
        recordHistory(endpoint, WebhookEndpointAction.CREATED, apiKeyId, now);

        return new CreatedWebhookEndpoint(endpoint, secret.plainText());
    }

    /** Endpoints ativos, do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<WebhookEndpoint> listActive(UUID merchantId) {
        return repository.findByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(merchantId);
    }

    /** Um endpoint do comerciante, inclusive removido (a resposta mostra deletedAt). */
    @Transactional(readOnly = true)
    public WebhookEndpoint get(UUID merchantId, UUID endpointId) {
        return repository.findByIdAndMerchantId(endpointId, merchantId)
                .orElseThrow(WebhookEndpointNotFoundException::new);
    }

    /**
     * Remoção lógica, idempotente: remover de novo não muda nada nem grava outro registro na linha do tempo.
     *
     * @throws WebhookEndpointNotFoundException endpoint inexistente ou de outro comerciante
     */
    @Transactional
    public void delete(UUID merchantId, UUID apiKeyId, UUID endpointId) {
        WebhookEndpoint endpoint = repository.lockByIdAndMerchantId(endpointId, merchantId)
                .orElseThrow(WebhookEndpointNotFoundException::new);
        Instant now = now();
        if (endpoint.delete(now)) {
            recordHistory(endpoint, WebhookEndpointAction.DELETED, apiKeyId, now);
        }
    }

    /**
     * Linha do tempo de um endpoint, do mais antigo para o mais recente. Continua disponível depois da remoção:
     * é justamente quando ela mais ajuda ("por que meus avisos pararam?").
     */
    @Transactional(readOnly = true)
    public List<WebhookEndpointHistory> history(UUID merchantId, UUID endpointId) {
        get(merchantId, endpointId); // 404 se não for do comerciante
        return historyRepository.findByEndpointIdAndMerchantIdOrderByOccurredAtAscIdAsc(endpointId, merchantId);
    }

    /**
     * Decifra o segredo de assinatura. Uso INTERNO (entrega de webhooks, Etapa 3); nunca devolver pela API.
     */
    public String signingSecretOf(WebhookEndpoint endpoint) {
        return cipher.decrypt(endpoint.getSecretCiphertext(), endpoint.getId().toString());
    }

    private void recordHistory(WebhookEndpoint endpoint, WebhookEndpointAction action, UUID apiKeyId, Instant now) {
        EndpointSnapshot snapshot = new EndpointSnapshot(
                endpoint.getUrl(), endpoint.getEnabledEvents().stream().map(EventType::value).toList());
        historyRepository.save(WebhookEndpointHistory.of(
                endpoint, action, apiKeyId, jsonMapper.writeValueAsString(snapshot), now));
    }

    /** Retrato gravado na linha do tempo. Intencionalmente SEM o segredo (nem cifrado, nem prefixo). */
    record EndpointSnapshot(String url, List<String> enabledEvents) {
    }

    private static List<EventType> parseEventTypes(List<String> values) {
        if (values == null) {
            return Arrays.asList(EventType.values());
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("enabledEvents must contain at least one event type");
        }
        return values.stream()
                .map(value -> {
                    if (value == null) {
                        throw new IllegalArgumentException("enabledEvents must not contain null");
                    }
                    return EventType.fromValue(value.trim());
                })
                .distinct()
                .toList();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
