-- V13: fim da transicao iniciada na V1 — os dados processuais moram em PROCESSOS.
-- O codigo parou de ler e escrever estas colunas (deploy conjunto com a entidade);
-- indices que as referenciam caem junto com as colunas.

ALTER TABLE pessoas_monitoradas
    DROP COLUMN IF EXISTS processo,
    DROP COLUMN IF EXISTS vara,
    DROP COLUMN IF EXISTS comarca,
    DROP COLUMN IF EXISTS data_decisao,
    DROP COLUMN IF EXISTS periodicidade,
    DROP COLUMN IF EXISTS data_comparecimento_inicial,
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS ultimo_comparecimento,
    DROP COLUMN IF EXISTS proximo_comparecimento;
