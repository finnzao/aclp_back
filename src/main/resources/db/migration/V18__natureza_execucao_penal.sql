-- V18: EXECUCAO_REGIME_ABERTO virou EXECUCAO_PENAL (a natureza cobre todas as modalidades).
-- Os CHECKs antigos caem antes do UPDATE, senao rejeitam o valor novo.

ALTER TABLE processos DROP CONSTRAINT IF EXISTS processos_natureza_vinculo_check;
ALTER TABLE processos DROP CONSTRAINT IF EXISTS chk_processo_natureza;
ALTER TABLE processos DROP CONSTRAINT IF EXISTS processos_cautelar_completo;

UPDATE processos SET natureza_vinculo = 'EXECUCAO_PENAL' WHERE natureza_vinculo = 'EXECUCAO_REGIME_ABERTO';

ALTER TABLE processos ADD CONSTRAINT chk_processo_natureza
    CHECK (natureza_vinculo IN ('CAUTELAR','EXECUCAO_PENAL'));

ALTER TABLE processos ADD CONSTRAINT processos_cautelar_completo CHECK (
    natureza_vinculo = 'EXECUCAO_PENAL'
    OR (periodicidade IS NOT NULL AND data_decisao IS NOT NULL));

CREATE OR REPLACE FUNCTION valida_natureza_execucao() RETURNS trigger AS $$
BEGIN
    IF (SELECT natureza_vinculo FROM processos WHERE id = NEW.processo_id) <> 'EXECUCAO_PENAL' THEN
        RAISE EXCEPTION 'Processo % nao e de execucao penal', NEW.processo_id;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
