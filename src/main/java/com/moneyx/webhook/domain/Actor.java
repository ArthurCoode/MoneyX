package com.moneyx.webhook.domain;

import java.util.UUID;

/**
 * Quem fez uma ação do comerciante na linha do tempo dos webhooks: uma API Key (integração) OU uma sessão do
 * dashboard (pessoa logada). Exatamente um dos dois. Ações do SISTEMA não têm autor (Actor nulo).
 */
public record Actor(UUID apiKeyId, UUID sessionId) {

    public Actor {
        if ((apiKeyId == null) == (sessionId == null)) {
            throw new IllegalArgumentException("An actor is exactly one of apiKeyId or sessionId");
        }
    }

    public static Actor apiKey(UUID apiKeyId) {
        return new Actor(apiKeyId, null);
    }

    public static Actor session(UUID sessionId) {
        return new Actor(null, sessionId);
    }
}
