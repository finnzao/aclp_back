-- V17: execucao sem agenda nao tem periodicidade; data da decisao e endereco opcionais na execucao.

ALTER TABLE processos
    ALTER COLUMN periodicidade DROP NOT NULL,
    ALTER COLUMN data_decisao DROP NOT NULL;

ALTER TABLE processos
    ADD CONSTRAINT processos_cautelar_completo CHECK (
        natureza_vinculo = 'EXECUCAO_REGIME_ABERTO'
        OR (periodicidade IS NOT NULL AND data_decisao IS NOT NULL));
