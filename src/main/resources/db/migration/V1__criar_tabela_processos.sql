-- =====================================================================
-- SCRIPT DE MIGRAÇÃO: Reestruturação ACLP - Criação da Tabela PROCESSOS
-- Executar manualmente no banco PostgreSQL na ordem dos passos
-- =====================================================================

-- =========================
-- PASSO 1: Criar tabela processos
-- =========================
CREATE TABLE IF NOT EXISTS processos (
    id BIGSERIAL PRIMARY KEY,
    custodiado_id BIGINT NOT NULL REFERENCES custodiados(id),
    numero_processo VARCHAR(25) NOT NULL,
    vara VARCHAR(100) NOT NULL,
    comarca VARCHAR(100) NOT NULL,
    data_decisao DATE NOT NULL,
    periodicidade INTEGER NOT NULL CHECK (periodicidade >= 1 AND periodicidade <= 365),
    data_comparecimento_inicial DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'EM_CONFORMIDADE',
    ultimo_comparecimento DATE,
    proximo_comparecimento DATE,
    situacao_processo VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    observacoes VARCHAR(500),
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- =========================
-- PASSO 2: Migrar dados existentes de custodiados para processos
-- =========================
INSERT INTO processos (
    custodiado_id, numero_processo, vara, comarca, data_decisao,
    periodicidade, data_comparecimento_inicial, status,
    ultimo_comparecimento, proximo_comparecimento,
    situacao_processo, criado_em
)
SELECT
    id,
    processo,
    vara,
    comarca,
    data_decisao,
    periodicidade,
    data_comparecimento_inicial,
    status,
    ultimo_comparecimento,
    proximo_comparecimento,
    CASE WHEN situacao = 'ARQUIVADO' THEN 'ENCERRADO' ELSE 'ATIVO' END,
    criado_em
FROM custodiados
WHERE processo IS NOT NULL;

-- =========================
-- PASSO 3: Adicionar processo_id em historico_comparecimentos
-- =========================

-- 3a. Adicionar coluna
ALTER TABLE historico_comparecimentos ADD COLUMN IF NOT EXISTS processo_id BIGINT;

-- 3b. Popular com o ID do processo correspondente
UPDATE historico_comparecimentos hc
SET processo_id = (
    SELECT p.id FROM processos p
    WHERE p.custodiado_id = hc.custodiado_id
    ORDER BY p.criado_em ASC LIMIT 1
)
WHERE hc.processo_id IS NULL;

-- 3c. Tornar NOT NULL e adicionar FK
ALTER TABLE historico_comparecimentos ALTER COLUMN processo_id SET NOT NULL;
ALTER TABLE historico_comparecimentos ADD CONSTRAINT fk_comparecimento_processo
    FOREIGN KEY (processo_id) REFERENCES processos(id);

-- =========================
-- PASSO 4: Criar índices da tabela processos
-- =========================
CREATE INDEX IF NOT EXISTS idx_processo_custodiado ON processos(custodiado_id);
CREATE INDEX IF NOT EXISTS idx_processo_numero ON processos(numero_processo);
CREATE INDEX IF NOT EXISTS idx_processo_status ON processos(status);
CREATE INDEX IF NOT EXISTS idx_processo_situacao ON processos(situacao_processo);
CREATE INDEX IF NOT EXISTS idx_processo_proximo ON processos(proximo_comparecimento);
CREATE INDEX IF NOT EXISTS idx_processo_status_proximo ON processos(status, proximo_comparecimento);
CREATE INDEX IF NOT EXISTS idx_processo_custodiado_situacao ON processos(custodiado_id, situacao_processo);

-- =========================
-- PASSO 5: (SOMENTE APÓS VALIDAÇÃO COMPLETA)
-- Remover colunas antigas - NÃO EXECUTAR ATÉ FRONTEND ESTAR ADAPTADO
-- =========================

-- DESCOMENTE ABAIXO APÓS VALIDAÇÃO:

-- ALTER TABLE historico_comparecimentos DROP COLUMN IF EXISTS custodiado_id;

-- ALTER TABLE custodiados DROP COLUMN IF EXISTS processo;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS vara;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS comarca;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS data_decisao;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS periodicidade;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS data_comparecimento_inicial;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS status;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS ultimo_comparecimento;
-- ALTER TABLE custodiados DROP COLUMN IF EXISTS proximo_comparecimento;
