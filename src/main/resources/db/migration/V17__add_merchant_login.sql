-- Fase 8, Etapa 8b: login do comerciante no dashboard (e-mail + senha).
--
-- 1. merchants.password_hash: Argon2id no formato do Spring Security ({argon2}$argon2id$v=19$m=...). NULL = ainda sem
--    senha (comerciantes anteriores à 8b): definem pelo link de redefinição de senha.
-- 2. merchant_sessions: sessão do dashboard. O token (mx_sess_...) NUNCA é guardado, só o SHA-256 (como as API Keys).
--    Expira 6 h sem uso (last_used_at) ou 24 h depois de criada (expires_at), o que vier primeiro; logout e redefinição
--    de senha preenchem revoked_at.
-- 3. password_reset_tokens: link de definir/redefinir senha (uso único, 1 h, só o hash).
-- 4. Trilha dos webhooks: o autor de uma ação pode ser uma API Key OU uma sessão do dashboard (exatamente um), e as
--    ações do sistema (DISABLED) continuam sem autor.

ALTER TABLE merchants ADD COLUMN password_hash VARCHAR(255);

CREATE TABLE merchant_sessions (
    id           UUID         PRIMARY KEY,
    merchant_id  UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,

    CONSTRAINT fk_merchant_sessions_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT uk_merchant_sessions_hash UNIQUE (token_hash),
    CONSTRAINT ck_merchant_sessions_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_merchant_sessions_times CHECK (last_used_at >= created_at AND expires_at > created_at)
);

-- Revogar todas as sessões ativas de um comerciante (redefinição de senha).
CREATE INDEX idx_merchant_sessions_merchant_active ON merchant_sessions (merchant_id) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY,
    merchant_id UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,

    CONSTRAINT fk_password_reset_tokens_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_tokens_used CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX idx_password_reset_tokens_merchant ON password_reset_tokens (merchant_id, created_at);

ALTER TABLE webhook_endpoint_history ADD COLUMN session_id UUID;
ALTER TABLE webhook_endpoint_history DROP CONSTRAINT ck_webhook_endpoint_history_actor;
ALTER TABLE webhook_endpoint_history
    ADD CONSTRAINT ck_webhook_endpoint_history_actor CHECK (
        CASE WHEN action = 'DISABLED'
             THEN api_key_id IS NULL AND session_id IS NULL
             ELSE (api_key_id IS NULL) <> (session_id IS NULL)
        END);
