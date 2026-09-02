-- Lacunas 2 e 5: FORAGIDO entra como valor de situacao (coluna ja e VARCHAR);
-- motivo estruturado do arquivamento explica por que a ficha saiu de circulacao.
ALTER TABLE custodiados ADD COLUMN IF NOT EXISTS motivo_arquivamento VARCHAR(30);
