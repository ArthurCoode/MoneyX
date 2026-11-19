-- Pagamentos (cobranças Pix) dos comerciantes.
--
-- amount é BIGINT em CENTAVOS (R$ 49,90 = 4990). Nunca usar tipos de ponto flutuante para dinheiro.
--
-- Os limites de negócio (mín. R$ 1,00, máx. R$ 100.000,00, prazo de 5 min a 24 h) ficam na aplicação,
-- pois são política e podem mudar sem migration. Aqui ficam apenas as INVARIANTES, que nunca podem
-- ser violadas, nem por um bug na aplicação nem por um UPDATE manual.

CREATE TABLE payments (
    id                  UUID         PRIMARY KEY,
    merchant_id         UUID         NOT NULL,
    amount              BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    description         VARCHAR(255),
    external_reference  VARCHAR(64),
    expires_at          TIMESTAMPTZ  NOT NULL,
    paid_at             TIMESTAMPTZ,
    canceled_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL,

    CONSTRAINT fk_payments_merchant   FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_payments_amount     CHECK (amount > 0),
    CONSTRAINT ck_payments_currency   CHECK (currency IN ('BRL')),
    CONSTRAINT ck_payments_status     CHECK (status IN ('PENDING', 'PAID', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_payments_expiration CHECK (expires_at > created_at),
    -- paid_at existe se, e somente se, o pagamento está PAID (o mesmo vale para canceled_at/CANCELED).
    CONSTRAINT ck_payments_paid_at     CHECK ((status = 'PAID') = (paid_at IS NOT NULL)),
    CONSTRAINT ck_payments_canceled_at CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL))
);

-- Consulta mais comum: "pagamentos do comerciante X, mais recentes primeiro".
CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at DESC);
