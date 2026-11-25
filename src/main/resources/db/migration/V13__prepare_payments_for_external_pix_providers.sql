-- Fase 7, Etapa 7c: pagamentos com cobrança Pix emitida por um PSP externo (Mercado Pago).
--
-- A criação passa a ter duas fases: o pagamento é gravado como CREATING (sem cobrança), o PSP é chamado FORA da
-- transação e só então a cobrança é anexada e o status vira PENDING. Por isso:
-- 1. novo status CREATING;
-- 2. txid e BR Code passam a ser opcionais (CREATING ainda não tem; EXPIRED pode não ter, se venceu antes do PSP
--    responder), mas andam sempre juntos;
-- 3. o txid passa a ser o do PSP (no Mercado Pago, ~21 caracteres minúsculos lidos do BR Code), até 35 caracteres;
-- 4. colunas do provedor: qual emitiu e os ids da order/pagamento nele (para consultar e cancelar).

ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status CHECK (status IN ('CREATING', 'PENDING', 'PAID', 'CANCELED', 'EXPIRED'));

ALTER TABLE payments DROP CONSTRAINT ck_payments_pix_txid;
ALTER TABLE payments
    ALTER COLUMN pix_txid TYPE VARCHAR(35),
    ALTER COLUMN pix_txid DROP NOT NULL,
    ALTER COLUMN pix_copy_paste DROP NOT NULL;
ALTER TABLE payments
    -- Campo 62.05 do BR Code: até 35 caracteres alfanuméricos (o PSP decide maiúsculas/minúsculas).
    ADD CONSTRAINT ck_payments_pix_txid CHECK (pix_txid ~ '^[A-Za-z0-9]{1,35}$'),
    ADD CONSTRAINT ck_payments_pix_together CHECK ((pix_txid IS NULL) = (pix_copy_paste IS NULL)),
    -- CREATING nunca tem cobrança; PENDING/PAID/CANCELED sempre têm; EXPIRED pode ter ou não.
    ADD CONSTRAINT ck_payments_pix_by_status CHECK (
        CASE status
            WHEN 'CREATING' THEN pix_txid IS NULL
            WHEN 'EXPIRED' THEN TRUE
            ELSE pix_txid IS NOT NULL
        END);

-- Pagamentos já existentes foram emitidos pelo simulador local. O DEFAULT só preenche as linhas atuais e é removido
-- em seguida: daqui em diante a aplicação sempre informa o provedor.
ALTER TABLE payments
    ADD COLUMN pix_provider        VARCHAR(20) NOT NULL DEFAULT 'sandbox',
    ADD COLUMN provider_order_id   VARCHAR(64),
    ADD COLUMN provider_payment_id VARCHAR(64);
ALTER TABLE payments ALTER COLUMN pix_provider DROP DEFAULT;
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_pix_provider CHECK (pix_provider IN ('sandbox', 'mercadopago')),
    -- Uma order do PSP pertence a um único pagamento.
    ADD CONSTRAINT uk_payments_provider_order UNIQUE (pix_provider, provider_order_id);

-- Rotina de recuperação: encontra rápido os pagamentos que ficaram em CREATING (o PSP falhou ou a aplicação caiu).
CREATE INDEX idx_payments_creating_created_at ON payments (created_at) WHERE status = 'CREATING';
