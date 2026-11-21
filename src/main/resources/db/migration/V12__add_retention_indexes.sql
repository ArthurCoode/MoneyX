-- Índices para a RETENÇÃO de eventos e entregas de webhook (jobs que apagam o que passou de 30 dias).
--
-- Sem eles, cada execução dos jobs leria as tabelas inteiras para achar as linhas antigas.

-- Eventos: percorridos do mais antigo para o mais novo, com cursor (created_at, id). O índice existente
-- (merchant_id, created_at) não serve: a retenção é de TODOS os comerciantes ao mesmo tempo.
CREATE INDEX idx_events_created_id ON events (created_at, id);

-- Entregas: só as FINALIZADAS podem ser apagadas. Índice parcial: as pendentes (que nunca são apagadas) nem entram.
CREATE INDEX idx_webhook_deliveries_finished_created ON webhook_deliveries (created_at)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELED');
