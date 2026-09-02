-- Espelho informativo do numero no SEEU, sem integracao
ALTER TABLE execucoes_penais ADD COLUMN IF NOT EXISTS numero_seeu VARCHAR(30);
