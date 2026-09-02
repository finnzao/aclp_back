-- V12: a tabela de PESSOAS deixa o nome impreciso "custodiados".
-- Juridicamente, custodiado e quem esta sob custodia (cautelar); quem cumpre
-- pena e apenado. O conceito da tabela sempre foi "pessoa monitorada".
-- Colunas FK (custodiado_id) e indices mantem os nomes antigos de proposito:
-- sao detalhe fisico documentado, renomea-los triplicaria o raio da mudanca.

ALTER TABLE custodiados RENAME TO pessoas_monitoradas;
