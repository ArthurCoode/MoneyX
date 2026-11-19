-- Comerciantes (estabelecimentos) que utilizam o MoneyX.
-- Raiz do isolamento multi-tenant: recursos futuros (API Keys, pagamentos, webhooks)
-- referenciarão esta tabela.

CREATE TABLE merchants (
    id          UUID         PRIMARY KEY,
    legal_name  VARCHAR(150) NOT NULL,
    document    VARCHAR(14)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL,

    CONSTRAINT uk_merchants_document UNIQUE (document),
    CONSTRAINT uk_merchants_email    UNIQUE (email),
    CONSTRAINT ck_merchants_document CHECK (document ~ '^[0-9]{11}$|^[0-9]{14}$'),
    CONSTRAINT ck_merchants_status   CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);
