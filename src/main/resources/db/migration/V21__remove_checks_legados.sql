-- V21: CHECKs criados pelo ddl-auto antes do Flyway ficaram com a lista de valores
-- da epoca. As migrations seguintes criaram o CHECK novo (chk_*) sem remover o
-- antigo, e o antigo continuava recusando os valores acrescentados depois:
--   FORAGIDO            -> "Declarar foragido" falhava
--   FALTA_JUSTIFICADA   -> registrar falta justificada falhava
--   EVASAO              -> incidente de evasao (foragido em execucao de pena) falhava

ALTER TABLE pessoas_monitoradas
    DROP CONSTRAINT IF EXISTS custodiados_situacao_check;

ALTER TABLE historico_comparecimentos
    DROP CONSTRAINT IF EXISTS historico_comparecimentos_tipo_validacao_check;

-- este nao tinha substituto: troca pelo CHECK com a lista atual do enum TipoIncidente
ALTER TABLE incidentes_execucao
    DROP CONSTRAINT IF EXISTS incidentes_execucao_tipo_check;
ALTER TABLE incidentes_execucao
    DROP CONSTRAINT IF EXISTS chk_incidente_tipo;
ALTER TABLE incidentes_execucao
    ADD CONSTRAINT chk_incidente_tipo CHECK (tipo IN (
        'DESCUMPRIMENTO_COMPARECIMENTO','MUDANCA_NAO_AUTORIZADA','NOVO_CRIME','EVASAO',
        'PEDIDO_AUTORIZACAO_VIAGEM','EXTINCAO_PUNIBILIDADE','OUTRO'));
