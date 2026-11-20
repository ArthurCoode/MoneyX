-- Eventos: registro imutável dos fatos relevantes de cada comerciante (ex.: "o pagamento X foi pago").
--
-- Duas funções:
-- 1. Histórico consultável pela API (GET /v1/events), inclusive para recuperar avisos perdidos.
-- 2. OUTBOX: o evento é gravado NA MESMA TRANSAÇÃO da mudança de estado que ele descreve. Ou os dois existem,
--    ou nenhum. A entrega por webhook (etapas seguintes) lê daqui depois, sem risco de "escrita dupla".
--
-- Linhas nunca são alteradas: o estado das ENTREGAS ficará em outra tabela.
-- resource_id: id do recurso descrito (hoje, sempre um pagamento). Sem FK de propósito: o módulo de eventos é
-- genérico e não conhece as tabelas dos outros módulos.

CREATE TABLE events (
    id           UUID         PRIMARY KEY,
    merchant_id  UUID         NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    resource_id  UUID         NOT NULL,
    data         JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_events_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_events_type CHECK (type IN ('payment.created', 'payment.paid', 'payment.canceled', 'payment.expired')),
    -- data é sempre um objeto JSON (o retrato do recurso), nunca um texto, número ou lista solta.
    CONSTRAINT ck_events_data_object CHECK (jsonb_typeof(data) = 'object')
);

-- Listagem: "eventos do comerciante X, mais recentes primeiro" (mesmo padrão de idx_payments_merchant_created).
CREATE INDEX idx_events_merchant_created ON events (merchant_id, created_at DESC);
