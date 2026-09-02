-- =====================================================================
-- V4: Discriminador cautelar × execução de pena (regime aberto)
-- O DEFAULT 'CAUTELAR' faz o backfill sozinho: toda linha existente
-- continua se comportando exatamente como hoje. Reversível.
-- =====================================================================

ALTER TABLE processos
    ADD COLUMN IF NOT EXISTS natureza_vinculo VARCHAR(30) NOT NULL DEFAULT 'CAUTELAR';

CREATE INDEX IF NOT EXISTS idx_processo_natureza ON processos (natureza_vinculo);
