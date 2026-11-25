-- Fase 7, Etapa 7d: conciliação com o PSP (Mercado Pago).
--
-- 1. provider_request: o corpo EXATO enviado ao PSP na criação. O PSP só devolve a mesma order para a mesma chave de
--    idempotência se o corpo for idêntico byte a byte; montar o corpo de novo numa repetição (com outra configuração,
--    ex.: sem o pagador de teste) gerava 409 idempotency_key_already_used (visto em 2026-11-24). Null no sandbox local.
-- 2. provider_checked_at: última consulta ao PSP. A rotina de conciliação consulta primeiro quem está há mais tempo
--    sem consulta (NULLS FIRST = nunca consultado).
-- 3. Um pagamento pode virar PAID sem BR Code guardado: a order foi paga no PSP, mas a resposta com o BR Code nunca
--    chegou ao MoneyX (o pagamento ainda estava CREATING). Nesse caso a order precisa estar registrada.

ALTER TABLE payments
    ADD COLUMN provider_request    TEXT,
    ADD COLUMN provider_checked_at TIMESTAMPTZ;

ALTER TABLE payments DROP CONSTRAINT ck_payments_pix_by_status;
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_pix_by_status CHECK (
        CASE status
            WHEN 'CREATING' THEN pix_txid IS NULL
            WHEN 'EXPIRED' THEN TRUE
            WHEN 'PAID' THEN pix_txid IS NOT NULL OR provider_order_id IS NOT NULL
            ELSE pix_txid IS NOT NULL
        END);

-- Conciliação: só pagamentos PENDING com cobrança num PSP externo.
CREATE INDEX idx_payments_pending_provider_check ON payments (provider_checked_at NULLS FIRST, created_at)
    WHERE status = 'PENDING' AND provider_order_id IS NOT NULL;
