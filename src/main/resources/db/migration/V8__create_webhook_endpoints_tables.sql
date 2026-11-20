-- Endpoints de webhook: para onde o MoneyX envia os eventos de cada comerciante (entrega na Etapa 3).
--
-- Duas tabelas (estado atual + linha do tempo):
-- - webhook_endpoints: o ESTADO ATUAL de cada endpoint. Remoção é LÓGICA (deleted_at): as entregas futuras vão
--   referenciar o endpoint, e o histórico precisa continuar apontando para algo que existe.
-- - webhook_endpoint_history: a LINHA DO TEMPO (trilha de auditoria). Só recebe INSERT: cada mudança vira uma
--   linha com o que aconteceu, quando e com qual API Key. Gravada na mesma transação da mudança.

CREATE TABLE webhook_endpoints (
    id                 UUID           PRIMARY KEY,
    merchant_id        UUID           NOT NULL,
    url                VARCHAR(2048)  NOT NULL,
    enabled_events     VARCHAR(50)[]  NOT NULL,
    -- Segredo de assinatura CIFRADO (AES-256-GCM; chave fora do banco). Nunca em texto puro.
    secret_ciphertext  VARCHAR(255)   NOT NULL,
    -- Início do segredo (ex.: whsec_ab12), em texto puro, só para o comerciante identificar qual é.
    secret_prefix      VARCHAR(20)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT         NOT NULL,

    CONSTRAINT fk_webhook_endpoints_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_webhook_endpoints_url CHECK (url LIKE 'https://%' OR url LIKE 'http://%'),
    -- Pelo menos um tipo, e somente tipos conhecidos (o operador <@ significa "está contido em").
    CONSTRAINT ck_webhook_endpoints_events CHECK (
        cardinality(enabled_events) >= 1
        AND enabled_events <@ ARRAY['payment.created', 'payment.paid', 'payment.canceled', 'payment.expired']::VARCHAR(50)[]
    ),
    CONSTRAINT ck_webhook_endpoints_deleted CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

-- Consultas: "endpoints ATIVOS do comerciante". Índice parcial: só as linhas não removidas.
CREATE INDEX idx_webhook_endpoints_merchant_active ON webhook_endpoints (merchant_id) WHERE deleted_at IS NULL;

CREATE TABLE webhook_endpoint_history (
    id           UUID          PRIMARY KEY,
    endpoint_id  UUID          NOT NULL,
    merchant_id  UUID          NOT NULL,
    action       VARCHAR(20)   NOT NULL,
    -- Qual API Key fez a mudança (o id, nunca o segredo). Sem FK: o registro de auditoria deve sobreviver
    -- independentemente do ciclo de vida da chave.
    api_key_id   UUID          NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL,
    -- Retrato do endpoint naquele momento (url e enabledEvents). NUNCA contém o segredo.
    snapshot     JSONB         NOT NULL,

    CONSTRAINT fk_webhook_endpoint_history_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints (id),
    CONSTRAINT fk_webhook_endpoint_history_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_webhook_endpoint_history_action CHECK (action IN ('CREATED', 'DELETED')),
    CONSTRAINT ck_webhook_endpoint_history_snapshot CHECK (jsonb_typeof(snapshot) = 'object')
);

CREATE INDEX idx_webhook_endpoint_history_endpoint ON webhook_endpoint_history (endpoint_id, occurred_at);
