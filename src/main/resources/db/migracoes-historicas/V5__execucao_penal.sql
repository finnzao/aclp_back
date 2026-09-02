-- V5: execucao penal (fase 2 do regime aberto).
-- data_termino_original = termino sem abatimentos (auditoria);
-- origem_calculo = como a pena entrou (SENTENCA | DOSIMETRIA_CALCULADA).

CREATE TABLE IF NOT EXISTS execucoes_penais (
    id                      BIGSERIAL PRIMARY KEY,
    processo_id             BIGINT NOT NULL UNIQUE
                            REFERENCES processos (id) ON DELETE CASCADE,

    numero_execucao         VARCHAR(25) NOT NULL UNIQUE,  -- PEC (CNJ)
    guia_recolhimento       VARCHAR(50),

    data_inicio_cumprimento DATE    NOT NULL,
    pena_total_dias         INTEGER NOT NULL CHECK (pena_total_dias > 0),

    dias_detraidos          INTEGER NOT NULL DEFAULT 0 CHECK (dias_detraidos >= 0),
    dias_remidos            INTEGER NOT NULL DEFAULT 0 CHECK (dias_remidos  >= 0),

    data_termino_original   DATE NOT NULL,
    data_termino_prevista   DATE NOT NULL,  -- materializada: consulta D-60 precisa de índice

    origem_calculo          VARCHAR(30) NOT NULL,  -- SENTENCA | DOSIMETRIA_CALCULADA
    situacao_execucao       VARCHAR(30) NOT NULL DEFAULT 'EM_CUMPRIMENTO',

    observacoes             VARCHAR(500),
    criado_em               TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em           TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_execucao_termino  ON execucoes_penais (data_termino_prevista);
CREATE INDEX IF NOT EXISTS idx_execucao_situacao ON execucoes_penais (situacao_execucao);
