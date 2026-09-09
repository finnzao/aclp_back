-- V9.1: reconciliacao para bancos que ficaram entre eras — criados pelo
-- ddl-auto mas com ultimo deploy ANTERIOR ao estado V8 (caso real: Supabase
-- do Render, sem motivo_arquivamento, que derrubou a V10 no deploy).
--
-- Roda em DOIS mundos, por isso tudo e idempotente e tolerante:
--   * producao defasada: tabela ainda se chama custodiados -> completa V3-V8;
--   * local atualizado (out-of-order, ja em V15): custodiados nem existe
--     (renomeada na V12) -> ALTER TABLE IF EXISTS vira no-op.
-- Requer spring.flyway.out-of-order=true.

-- era V3: identidade publica.
-- O tipo da coluna diverge por origem: ddl-auto criou uuid nativo (producao),
-- o script manual V3 criou VARCHAR(36) (local). O UPDATE respeita o que houver.
ALTER TABLE IF EXISTS custodiados ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
DO $$
DECLARE tipo TEXT;
BEGIN
    IF to_regclass('custodiados') IS NULL THEN RETURN; END IF;

    SELECT data_type INTO tipo FROM information_schema.columns
     WHERE table_name = 'custodiados' AND column_name = 'public_id';

    IF tipo = 'uuid' THEN
        UPDATE custodiados SET public_id = gen_random_uuid() WHERE public_id IS NULL;
    ELSE
        UPDATE custodiados SET public_id = gen_random_uuid()::VARCHAR WHERE public_id IS NULL;
    END IF;

    ALTER TABLE custodiados ALTER COLUMN public_id SET NOT NULL;

    -- ddl-auto pode ja ter criado um unique com nome proprio; nao duplicar indice
    IF NOT EXISTS (SELECT 1 FROM pg_indexes
                   WHERE tablename = 'custodiados'
                     AND indexdef LIKE 'CREATE UNIQUE%(public_id)%') THEN
        ALTER TABLE custodiados ADD CONSTRAINT uq_custodiado_public_id UNIQUE (public_id);
    END IF;
END $$;

-- era V4: natureza do vinculo
ALTER TABLE processos ADD COLUMN IF NOT EXISTS natureza_vinculo VARCHAR(30) NOT NULL DEFAULT 'CAUTELAR';

-- era V5: execucao penal
CREATE TABLE IF NOT EXISTS execucoes_penais (
    id                      BIGSERIAL PRIMARY KEY,
    processo_id             BIGINT NOT NULL UNIQUE REFERENCES processos (id) ON DELETE CASCADE,
    numero_execucao         VARCHAR(25) NOT NULL UNIQUE,
    guia_recolhimento       VARCHAR(50),
    data_inicio_cumprimento DATE    NOT NULL,
    pena_total_dias         INTEGER NOT NULL CHECK (pena_total_dias > 0),
    dias_detraidos          INTEGER NOT NULL DEFAULT 0 CHECK (dias_detraidos >= 0),
    dias_remidos            INTEGER NOT NULL DEFAULT 0 CHECK (dias_remidos  >= 0),
    data_termino_original   DATE NOT NULL,
    data_termino_prevista   DATE NOT NULL,
    origem_calculo          VARCHAR(30) NOT NULL,
    situacao_execucao       VARCHAR(30) NOT NULL DEFAULT 'EM_CUMPRIMENTO',
    observacoes             VARCHAR(500),
    criado_em               TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em           TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_execucao_termino  ON execucoes_penais (data_termino_prevista);
CREATE INDEX IF NOT EXISTS idx_execucao_situacao ON execucoes_penais (situacao_execucao);

-- era V6: espelho do SEEU
ALTER TABLE execucoes_penais ADD COLUMN IF NOT EXISTS numero_seeu VARCHAR(30);

-- era V7: incidentes da execucao
CREATE TABLE IF NOT EXISTS incidentes_execucao (
    id                  BIGSERIAL PRIMARY KEY,
    execucao_penal_id   BIGINT NOT NULL REFERENCES execucoes_penais (id) ON DELETE CASCADE,
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

-- era V8: motivo estruturado do arquivamento (a coluna que derrubou o deploy)
ALTER TABLE IF EXISTS custodiados ADD COLUMN IF NOT EXISTS motivo_arquivamento VARCHAR(30);
