-- =====================================================================
-- MIGRAÇÃO V3: Adicionar public_id (UUID) à tabela custodiados
--
-- MOTIVO:
--   IDs sequenciais (1, 2, 3…) são previsíveis e expõem informações
--   sobre o volume de dados do sistema.  Esta migração adiciona um
--   campo UUID como identificador público para uso na API/frontend,
--   mantendo o BIGSERIAL interno como chave primária (melhor para FKs).
--
-- ESTRATÉGIA:
--   PK interna:  id BIGSERIAL  → usado em todas as FKs (performance)
--   ID público:  public_id UUID → exposto na API, URLs e frontend
--
-- IMPORTANTE: Executar após o deploy da aplicação com ddl-auto=update
--   (o Hibernate cria a coluna).  O UPDATE abaixo popula registros
--   existentes que ainda não têm public_id.
-- =====================================================================

-- 1. Adicionar coluna (se ainda não existir — Hibernate pode criar via ddl-auto)
ALTER TABLE custodiados
    ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);

-- 2. Popular custodiados existentes que não têm public_id
UPDATE custodiados
SET public_id = gen_random_uuid()::VARCHAR
WHERE public_id IS NULL;

-- 3. Garantir NOT NULL e UNIQUE após popular
ALTER TABLE custodiados
    ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE custodiados
    DROP CONSTRAINT IF EXISTS uq_custodiado_public_id;

ALTER TABLE custodiados
    ADD CONSTRAINT uq_custodiado_public_id UNIQUE (public_id);

-- 4. Índice para busca por public_id (queries do controller)
CREATE INDEX IF NOT EXISTS idx_custodiado_public_id
    ON custodiados (public_id);

-- =====================================================================
-- VERIFICAÇÃO (opcional — execute para confirmar)
-- =====================================================================
-- SELECT id, public_id, nome FROM custodiados LIMIT 10;
-- SELECT COUNT(*) FROM custodiados WHERE public_id IS NULL;  -- deve ser 0
