-- =====================================================================
-- ÍNDICES DE PERFORMANCE ADICIONAIS
-- Executar após migração principal (V1)
-- =====================================================================

-- Busca textual otimizada (PostgreSQL)
CREATE INDEX IF NOT EXISTS idx_custodiados_nome_lower ON custodiados(LOWER(nome));
CREATE INDEX IF NOT EXISTS idx_busca_custodiado ON custodiados USING gin(to_tsvector('portuguese', nome || ' ' || COALESCE(cpf, '')));
CREATE INDEX IF NOT EXISTS idx_busca_processo ON processos USING gin(to_tsvector('portuguese', numero_processo));

-- Índice parcial para job de inadimplentes (evita full scan)
CREATE INDEX IF NOT EXISTS idx_processos_atrasados ON processos(proximo_comparecimento)
    WHERE situacao_processo = 'ATIVO' AND status = 'EM_CONFORMIDADE';

-- Histórico de comparecimentos
CREATE INDEX IF NOT EXISTS idx_comparecimento_processo ON historico_comparecimentos(processo_id);
CREATE INDEX IF NOT EXISTS idx_comparecimento_data_desc ON historico_comparecimentos(data_comparecimento DESC);
CREATE INDEX IF NOT EXISTS idx_comparecimento_processo_data ON historico_comparecimentos(processo_id, data_comparecimento DESC);

-- Histórico de endereços (busca por custodiado + ativo)
CREATE INDEX IF NOT EXISTS idx_hist_endereco_custodiado_ativo ON historico_enderecos(custodiado_id, ativo, data_inicio DESC);
