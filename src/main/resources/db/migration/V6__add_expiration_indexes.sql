-- Índices para os jobs de manutenção (Fase 4, Etapa 5). Apenas cria índices: nenhum dado ou coluna muda.

-- Job de expiração: "pagamentos PENDING com expires_at <= agora, os mais antigos primeiro".
-- Índice PARCIAL: guarda só as linhas PENDING. Como a maioria dos pagamentos termina em estado final
-- (PAID, CANCELED, EXPIRED), o índice fica pequeno, e cada linha sai dele quando deixa de ser PENDING.
CREATE INDEX idx_payments_pending_expires_at ON payments (expires_at) WHERE status = 'PENDING';

-- Limpeza das chaves de idempotência vencidas: "expires_at <= agora".
CREATE INDEX idx_payment_idempotency_keys_expires_at ON payment_idempotency_keys (expires_at);
