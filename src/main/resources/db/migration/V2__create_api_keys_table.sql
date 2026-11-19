-- API Keys usadas pelos comerciantes para autenticar requisições.
-- A chave completa NUNCA é armazenada: apenas seu hash SHA-256 e um prefixo curto para identificação.

CREATE TABLE api_keys (
    id           UUID         PRIMARY KEY,
    merchant_id  UUID         NOT NULL,
    key_prefix   VARCHAR(20)  NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    version      BIGINT       NOT NULL,

    CONSTRAINT fk_api_keys_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    -- O índice criado pela constraint UNIQUE torna a busca por hash (feita em toda requisição) eficiente.
    CONSTRAINT uk_api_keys_hash     UNIQUE (key_hash),
    CONSTRAINT ck_api_keys_hash     CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_api_keys_revoked  CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX idx_api_keys_merchant_id ON api_keys (merchant_id);
