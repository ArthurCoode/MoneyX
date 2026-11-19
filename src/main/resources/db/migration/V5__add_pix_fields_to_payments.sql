-- Dados da cobrança Pix de cada pagamento.
--
-- pix_txid:       identificador da cobrança (campo 62.05 do BR Code), usado para conciliar o pagamento recebido.
-- pix_copy_paste: BR Code completo ("Pix copia e cola"), padrão EMV do Banco Central. O comerciante o exibe
--                 como texto ou gera a imagem do QR code a partir dele.
--
-- NOT NULL direto é seguro porque a tabela está vazia (verificado antes da migration). Em uma tabela
-- com dados, seria preciso adicionar a coluna como NULL, preencher os registros e só então tornar NOT NULL.

ALTER TABLE payments
    ADD COLUMN pix_txid       VARCHAR(25)  NOT NULL,
    ADD COLUMN pix_copy_paste VARCHAR(512) NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_pix_txid UNIQUE (pix_txid),
    -- BR Code estático: txid de até 25 caracteres alfanuméricos. Usamos sempre 25, maiúsculas e dígitos.
    ADD CONSTRAINT ck_payments_pix_txid CHECK (pix_txid ~ '^[A-Z0-9]{25}$'),
    -- Todo BR Code começa com o indicador de formato "000201" e termina com o CRC: "6304" + 4 hex.
    ADD CONSTRAINT ck_payments_pix_copy_paste CHECK (pix_copy_paste ~ '^000201.*6304[0-9A-F]{4}$');
