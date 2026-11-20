-- Retentativas, desativação automática e reativação de endpoints de webhook (Fase 5, Etapa 4).
-- Só ADICIONA colunas e troca constraints. Nenhum dado é apagado; as linhas existentes continuam válidas.

-- Endpoint: desativação (automática) e início da sequência atual de falhas.
ALTER TABLE webhook_endpoints
    ADD COLUMN disabled_at   TIMESTAMPTZ,
    -- Início da sequência ATUAL de falhas (zerado a cada entrega bem-sucedida). Base da desativação automática.
    ADD COLUMN failing_since TIMESTAMPTZ;

ALTER TABLE webhook_endpoints
    ADD CONSTRAINT ck_webhook_endpoints_disabled CHECK (disabled_at IS NULL OR disabled_at >= created_at);

-- Linha do tempo: novas ações.
ALTER TABLE webhook_endpoint_history DROP CONSTRAINT ck_webhook_endpoint_history_action;
ALTER TABLE webhook_endpoint_history
    ADD CONSTRAINT ck_webhook_endpoint_history_action CHECK (action IN ('CREATED', 'DELETED', 'DISABLED', 'ENABLED'));

-- Ações do SISTEMA não têm API Key. A regra fica explícita: só DISABLED (automática) é sem chave, e ela é sempre sem chave.
ALTER TABLE webhook_endpoint_history ALTER COLUMN api_key_id DROP NOT NULL;
ALTER TABLE webhook_endpoint_history
    ADD CONSTRAINT ck_webhook_endpoint_history_actor CHECK ((action = 'DISABLED') = (api_key_id IS NULL));

-- Consulta de entregas de um endpoint, mais recentes primeiro.
CREATE INDEX idx_webhook_deliveries_endpoint_created ON webhook_deliveries (endpoint_id, created_at DESC);
