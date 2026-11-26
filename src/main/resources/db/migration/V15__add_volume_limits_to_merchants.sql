-- Fase 7 (pendências): limite acumulado de volume POR COMERCIANTE.
--
-- Opcionais: NULL = vale o limite global (moneyx.payments.volume-limit.*). Em centavos. Não existe área de operador
-- ainda: o limite de um comerciante é ajustado por SQL, por exemplo
--   UPDATE merchants SET daily_volume_limit = 50000000, monthly_volume_limit = 500000000 WHERE id = '...';
-- A aplicação nunca grava estas colunas (a entidade as mapeia só para leitura).

ALTER TABLE merchants
    ADD COLUMN daily_volume_limit   BIGINT,
    ADD COLUMN monthly_volume_limit BIGINT;

ALTER TABLE merchants
    ADD CONSTRAINT ck_merchants_daily_volume_limit CHECK (daily_volume_limit IS NULL OR daily_volume_limit > 0),
    ADD CONSTRAINT ck_merchants_monthly_volume_limit CHECK (monthly_volume_limit IS NULL OR monthly_volume_limit > 0),
    -- Um limite diário maior que o mensal nunca seria alcançado: provavelmente erro de digitação do operador.
    ADD CONSTRAINT ck_merchants_volume_limits_order CHECK (
        daily_volume_limit IS NULL OR monthly_volume_limit IS NULL OR daily_volume_limit <= monthly_volume_limit);
