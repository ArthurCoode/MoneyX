-- Fase 7 (pendências): verificação do e-mail do comerciante, exigida para criar chaves mx_live_.
--
-- O token vai por e-mail (link) e NUNCA é guardado: só o hash SHA-256, como as API Keys. Uso único, validade de 24 h.
-- Pedir um novo token invalida os anteriores (used_at preenchido sem verificar o e-mail).

ALTER TABLE merchants ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE merchants
    ADD CONSTRAINT ck_merchants_email_verified CHECK (email_verified_at IS NULL OR email_verified_at >= created_at);

CREATE TABLE email_verification_tokens (
    id          UUID         PRIMARY KEY,
    merchant_id UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,

    -- CASCADE: o token só existe para o comerciante (comerciantes não são apagados pela aplicação; encerrar = status).
    CONSTRAINT fk_email_verification_tokens_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verification_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_email_verification_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_email_verification_tokens_used CHECK (used_at IS NULL OR used_at >= created_at)
);

-- Último token do comerciante (reenvio com intervalo mínimo) e invalidação dos pendentes.
CREATE INDEX idx_email_verification_tokens_merchant ON email_verification_tokens (merchant_id, created_at);
