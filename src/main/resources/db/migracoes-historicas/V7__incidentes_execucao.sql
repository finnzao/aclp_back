-- V7: incidentes da execucao (rito de justificacao, LEP 118 p2).
-- Audiencia e registrada, nunca designada; sem anexo (nao ha storage).
CREATE TABLE IF NOT EXISTS incidentes_execucao (
    id                  BIGSERIAL PRIMARY KEY,
    execucao_penal_id   BIGINT NOT NULL
                        REFERENCES execucoes_penais (id) ON DELETE CASCADE,

    tipo                VARCHAR(40)  NOT NULL,
    data_ocorrencia     DATE         NOT NULL,
    descricao           VARCHAR(1000) NOT NULL,

    data_audiencia      DATE,
    resultado           VARCHAR(30)  NOT NULL DEFAULT 'AGUARDANDO',
    data_decisao        DATE,

    registrado_por      VARCHAR(100) NOT NULL,
    decidido_por        VARCHAR(100),
    criado_em           TIMESTAMP NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_incidente_execucao
    ON incidentes_execucao (execucao_penal_id, data_ocorrencia DESC);
CREATE INDEX IF NOT EXISTS idx_incidente_resultado ON incidentes_execucao (resultado);
