-- Chaves de idempotência da criação de pagamentos (header Idempotency-Key).
--
-- Garante que repetir POST /v1/payments com a mesma chave (ex.: retentativa após timeout de rede)
-- nunca gere uma segunda cobrança: a requisição repetida recebe o pagamento criado na primeira.
--
-- - A chave vale POR COMERCIANTE: UNIQUE (merchant_id, idempotency_key).
-- - request_fingerprint: SHA-256 do corpo da requisição. Mesma chave + corpo diferente = erro do cliente.
-- - Após expires_at (24 h) a mesma chave pode ser reutilizada para um novo pagamento.
-- - A linha e o pagamento são gravados na MESMA transação: ou ambos existem, ou nenhum.

CREATE TABLE payment_idempotency_keys (
    id                   UUID          PRIMARY KEY,
    merchant_id          UUID          NOT NULL,
    idempotency_key      VARCHAR(255)  NOT NULL,
    request_fingerprint  VARCHAR(64)   NOT NULL,
    payment_id           UUID          NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    expires_at           TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_payment_idempotency_keys UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT fk_payment_idempotency_keys_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    -- DEFERRABLE INITIALLY DEFERRED: a FK só é conferida no COMMIT. A linha de idempotência é gravada
    -- ANTES do pagamento (é ela que "reserva" a chave), e o pagamento é inserido logo em seguida,
    -- na mesma transação. No commit, o pagamento já existe e a FK é satisfeita.
    CONSTRAINT fk_payment_idempotency_keys_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_payment_idempotency_keys_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_idempotency_keys_key_length CHECK (char_length(idempotency_key) BETWEEN 1 AND 255),
    CONSTRAINT ck_payment_idempotency_keys_expiration CHECK (expires_at > created_at)
);
