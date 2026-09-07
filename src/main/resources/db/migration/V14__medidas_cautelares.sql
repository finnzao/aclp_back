-- V14: a camada da CUSTODIA que faltava (PROPOSTA_SCHEMA). O juiz costuma impor
-- varias medidas juntas (art. 319 CPP) — 1:N com o processo. O sistema REGISTRA;
-- fiscaliza so o comparecimento (decisao 4 da vara). Tambem serve para anotar
-- condicoes do regime aberto (LEP art. 115) em processos de execucao.

CREATE TABLE IF NOT EXISTS medidas_cautelares (
    id              BIGSERIAL PRIMARY KEY,
    processo_id     BIGINT NOT NULL REFERENCES processos (id) ON DELETE CASCADE,
    tipo_medida     VARCHAR(40) NOT NULL,
    data_imposicao  DATE NOT NULL,
    data_revogacao  DATE,
    detalhe         VARCHAR(500),
    registrado_por  VARCHAR(100) NOT NULL,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_medida_tipo CHECK (tipo_medida IN (
        'COMPARECIMENTO_PERIODICO','PROIBICAO_FREQUENTAR_LUGARES','PROIBICAO_CONTATO',
        'PROIBICAO_AUSENTAR_COMARCA','RECOLHIMENTO_NOTURNO','SUSPENSAO_FUNCAO',
        'INTERNACAO_PROVISORIA','FIANCA','MONITORACAO_ELETRONICA','OUTRA')),
    CONSTRAINT chk_medida_datas CHECK (data_revogacao IS NULL OR data_revogacao >= data_imposicao)
);

CREATE INDEX IF NOT EXISTS idx_medida_processo ON medidas_cautelares (processo_id);
