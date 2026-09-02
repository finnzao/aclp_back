-- V11: invariante que CHECK nao alcanca (atravessa tabela):
-- execucao penal so pode anexar em processo de natureza EXECUCAO_REGIME_ABERTO.

CREATE OR REPLACE FUNCTION valida_natureza_execucao() RETURNS trigger AS $$
BEGIN
    IF (SELECT natureza_vinculo FROM processos WHERE id = NEW.processo_id)
       <> 'EXECUCAO_REGIME_ABERTO' THEN
        RAISE EXCEPTION 'Processo % nao e de execucao penal', NEW.processo_id;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_execucao_natureza ON execucoes_penais;
CREATE TRIGGER trg_execucao_natureza
    BEFORE INSERT OR UPDATE OF processo_id ON execucoes_penais
    FOR EACH ROW EXECUTE FUNCTION valida_natureza_execucao();
