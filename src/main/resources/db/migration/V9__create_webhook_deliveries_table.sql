-- Entregas de webhook: o "Outbox" propriamente dito. Uma linha por (evento, endpoint interessado), criada NA MESMA
-- TRANSAÇÃO do evento. O evento é imutável; o estado da entrega (pendente, entregue, falhou) fica aqui.
--
-- Envio com RESERVA COM PRAZO (lease): o job marca lease_until/lease_token numa transação curta, faz o POST FORA de
-- transação e grava o resultado em outra transação curta, conferindo o lease_token. Se a aplicação cair no meio,
-- a reserva vence e outra execução tenta de novo (entrega "pelo menos uma vez").

CREATE TABLE webhook_deliveries (
    id                UUID          PRIMARY KEY,
    event_id          UUID          NOT NULL,
    endpoint_id       UUID          NOT NULL,
    merchant_id       UUID          NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    attempts          INTEGER       NOT NULL,
    next_attempt_at   TIMESTAMPTZ   NOT NULL,
    lease_until       TIMESTAMPTZ,
    lease_token       UUID,
    last_attempt_at   TIMESTAMPTZ,
    last_status_code  INTEGER,
    last_error        VARCHAR(50),
    last_duration_ms  INTEGER,
    delivered_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL,

    CONSTRAINT fk_webhook_deliveries_event    FOREIGN KEY (event_id)    REFERENCES events (id),
    CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints (id),
    CONSTRAINT fk_webhook_deliveries_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    -- Nunca duas entregas do mesmo evento para o mesmo endpoint.
    CONSTRAINT uk_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT ck_webhook_deliveries_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    CONSTRAINT ck_webhook_deliveries_attempts CHECK (attempts >= 0),
    -- delivered_at existe se, e somente se, a entrega teve sucesso.
    CONSTRAINT ck_webhook_deliveries_delivered CHECK ((status = 'SUCCEEDED') = (delivered_at IS NOT NULL)),
    -- Reserva: ou as duas colunas existem, ou nenhuma.
    CONSTRAINT ck_webhook_deliveries_lease CHECK ((lease_until IS NULL) = (lease_token IS NULL))
);

-- Job de entrega: "PENDING com next_attempt_at <= agora". Índice parcial: só as pendentes.
CREATE INDEX idx_webhook_deliveries_pending ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
