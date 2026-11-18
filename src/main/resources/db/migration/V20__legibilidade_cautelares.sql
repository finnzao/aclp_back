-- V20: legibilidade do armazenamento. Nenhum dado muda de lugar.
-- 1) medidas_cautelares deixa de ficar vazia: todo processo CAUTELAR tem a medida
--    COMPARECIMENTO_PERIODICO (e a propria razao de ele estar no sistema).
-- 2) Views de leitura por natureza, para consultar sem cruzar tabelas na mao.
-- 3) Descricoes nas tabelas (aparecem no Table Editor).

-- 1a) Gatilho no banco, nao no Java: cobre os tres pontos do backend que criam
-- processo e tambem os INSERTs feitos direto por SQL.
CREATE OR REPLACE FUNCTION registra_medida_comparecimento() RETURNS trigger AS $$
BEGIN
    IF NEW.natureza_vinculo = 'CAUTELAR' THEN
        INSERT INTO medidas_cautelares (processo_id, tipo_medida, data_imposicao, registrado_por)
        VALUES (NEW.id, 'COMPARECIMENTO_PERIODICO', NEW.data_decisao, 'Sistema SCC');
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_medida_comparecimento ON processos;
CREATE TRIGGER trg_medida_comparecimento
    AFTER INSERT ON processos
    FOR EACH ROW EXECUTE FUNCTION registra_medida_comparecimento();

-- 1b) Processos cautelares ja existentes. So os ativos: de um encerrado nao se
-- sabe a data de revogacao, e a medida apareceria como vigente.
INSERT INTO medidas_cautelares (processo_id, tipo_medida, data_imposicao, registrado_por)
SELECT p.id, 'COMPARECIMENTO_PERIODICO', p.data_decisao, 'Sistema SCC'
FROM processos p
WHERE p.natureza_vinculo = 'CAUTELAR'
  AND p.situacao_processo = 'ATIVO'
  AND NOT EXISTS (SELECT 1 FROM medidas_cautelares m
                  WHERE m.processo_id = p.id AND m.tipo_medida = 'COMPARECIMENTO_PERIODICO');

-- 2) Uma linha por processo. security_invoker: a view nao da acesso a quem nao
-- tem acesso as tabelas.
CREATE OR REPLACE VIEW vw_cautelares WITH (security_invoker = true) AS
SELECT pm.id   AS pessoa_id,
       pm.nome,
       pm.cpf,
       pm.rg,
       pm.contato,
       pm.situacao AS situacao_pessoa,
       p.id    AS processo_id,
       p.numero_processo,
       p.vara,
       p.comarca,
       p.situacao_processo,
       p.data_decisao,
       p.periodicidade,
       p.ultimo_comparecimento,
       p.proximo_comparecimento,
       (p.situacao_processo = 'ATIVO' AND p.proximo_comparecimento < CURRENT_DATE) AS inadimplente,
       (SELECT string_agg(m.tipo_medida, ', ' ORDER BY m.tipo_medida)
          FROM medidas_cautelares m
         WHERE m.processo_id = p.id AND m.data_revogacao IS NULL) AS medidas_vigentes
FROM processos p
JOIN pessoas_monitoradas pm ON pm.id = p.custodiado_id
WHERE p.natureza_vinculo = 'CAUTELAR';

CREATE OR REPLACE VIEW vw_execucoes WITH (security_invoker = true) AS
SELECT pm.id   AS pessoa_id,
       pm.nome,
       pm.cpf,
       pm.rg,
       pm.contato,
       pm.situacao AS situacao_pessoa,
       p.id    AS processo_id,
       p.numero_processo,
       p.vara,
       p.comarca,
       p.situacao_processo,
       e.id    AS execucao_id,
       e.numero_execucao,
       e.modalidade,
       e.situacao_execucao,
       e.data_inicio_cumprimento,
       e.pena_total_dias,
       e.dias_detraidos,
       e.dias_remidos,
       e.data_termino_prevista,
       p.periodicidade,
       p.ultimo_comparecimento,
       p.proximo_comparecimento
FROM processos p
JOIN pessoas_monitoradas pm ON pm.id = p.custodiado_id
LEFT JOIN execucoes_penais e ON e.processo_id = p.id
WHERE p.natureza_vinculo = 'EXECUCAO_PENAL';

-- 3) Descricoes
COMMENT ON TABLE pessoas_monitoradas IS
    'Todas as pessoas acompanhadas, de cautelar e de execucao de pena. A natureza esta em processos.natureza_vinculo.';
COMMENT ON TABLE processos IS
    'Vinculo da pessoa com a vara (CAUTELAR ou EXECUCAO_PENAL) e a agenda de comparecimento. Leitura por natureza: vw_cautelares e vw_execucoes.';
COMMENT ON TABLE execucoes_penais IS
    'Dados da pena, 1:1 com processos de natureza EXECUCAO_PENAL.';
COMMENT ON TABLE medidas_cautelares IS
    'Medidas impostas no processo (CPP art. 319), 1:N. COMPARECIMENTO_PERIODICO e criada automaticamente em todo processo CAUTELAR.';
COMMENT ON VIEW vw_cautelares IS
    'Leitura: pessoa + processo cautelar + medidas vigentes. Uma linha por processo.';
COMMENT ON VIEW vw_execucoes IS
    'Leitura: pessoa + processo de execucao + dados da pena. Uma linha por processo.';
