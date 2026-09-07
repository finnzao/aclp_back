-- V15: indices redundantes ou de cardinalidade inutil (REVIEW_PERFORMANCE_DB).
-- Regra: indice (A) e peso morto quando existe (A,B); cardinalidade 2-3 nunca
-- vence seq scan. Cada drop tem um composto/parcial que o substitui.
-- Os @Index correspondentes sairam das entidades no mesmo deploy.

DROP INDEX IF EXISTS idx_historico_custodiado;    -- prefixo de idx_historico_custodiado_data
DROP INDEX IF EXISTS idx_processo_custodiado;     -- prefixo de idx_processo_custodiado_situacao
DROP INDEX IF EXISTS idx_processo_situacao;       -- cardinalidade 3
DROP INDEX IF EXISTS idx_processo_status_proximo; -- status e cache; consultas derivam por data
DROP INDEX IF EXISTS idx_processo_natureza;       -- os EXISTS entram por custodiado_id
DROP INDEX IF EXISTS idx_custodiado_situacao;     -- cardinalidade 3, ~tudo ATIVO
