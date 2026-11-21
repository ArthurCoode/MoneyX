-- Ordenação estável da linha do tempo dos endpoints de webhook.
--
-- Antes: ORDER BY occurred_at, id. Duas ações no MESMO microssegundo (ex.: DISABLED e ENABLED) eram desempatadas pelo
-- id, um UUID aleatório: a ordem exibida podia contradizer a ordem real.
--
-- Agora: seq é numerada pelo PRÓPRIO banco a cada INSERT (GENERATED ALWAYS: a aplicação não consegue escolher nem
-- alterar o valor). A ordem passa a ser occurred_at, seq.
--
-- Por que seq reflete a ordem real: o número é atribuído no INSERT (não no COMMIT), mas toda escrita na linha do tempo
-- de um endpoint acontece com a linha do endpoint (ou do comerciante, na criação) trancada; para um mesmo endpoint as
-- escritas são sempre uma depois da outra.
--
-- Linhas já existentes recebem números na ordem em que o PostgreSQL as lê (arbitrária). Para elas o desempate continua
-- tão arbitrário quanto era com o id: só as novas ganham a garantia.

ALTER TABLE webhook_endpoint_history
    ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;

ALTER TABLE webhook_endpoint_history
    ADD CONSTRAINT uk_webhook_endpoint_history_seq UNIQUE (seq);
