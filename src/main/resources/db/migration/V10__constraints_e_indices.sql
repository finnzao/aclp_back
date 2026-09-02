-- V10: regras de negocio e indices que faltavam no banco (plano REFATORACAO_SCHEMA).

-- CPF unico apenas entre ATIVOS: era so um if no service (corrida criava duplicata).
-- Re-cadastro apos arquivamento continua permitido.
CREATE UNIQUE INDEX IF NOT EXISTS uq_custodiado_cpf_ativo
    ON custodiados (cpf)
    WHERE situacao = 'ATIVO' AND cpf IS NOT NULL;

-- Caminho do job diario de inadimplencia: so processos ATIVOS sao cobrados.
CREATE INDEX IF NOT EXISTS idx_processo_cobranca
    ON processos (proximo_comparecimento)
    WHERE situacao_processo = 'ATIVO';

-- Indices soltos de baixa cardinalidade ou cobertos por compostos: peso morto de escrita.
DROP INDEX IF EXISTS idx_historico_processo;         -- coberto por idx_historico_processo_data
DROP INDEX IF EXISTS idx_historico_data;
DROP INDEX IF EXISTS idx_historico_tipo;             -- cardinalidade 4
DROP INDEX IF EXISTS idx_historico_mudanca_endereco; -- cardinalidade 2
DROP INDEX IF EXISTS idx_processo_status;            -- cardinalidade 2
DROP INDEX IF EXISTS idx_processo_proximo;           -- substituido pelo parcial acima

-- Enums governados pelo codigo Java, protegidos por CHECK (evoluem por migration).
ALTER TABLE custodiados
    ADD CONSTRAINT chk_custodiado_situacao
    CHECK (situacao IN ('ATIVO','ARQUIVADO','FORAGIDO')) NOT VALID;
ALTER TABLE custodiados VALIDATE CONSTRAINT chk_custodiado_situacao;

ALTER TABLE custodiados
    ADD CONSTRAINT chk_custodiado_motivo_arq
    CHECK (motivo_arquivamento IS NULL OR motivo_arquivamento IN
        ('OBITO_COMPROVADO','PRISAO_OUTRO_PROCESSO','DECISAO_JUDICIAL',
         'EXTINCAO_PUNIBILIDADE','OUTRO')) NOT VALID;
ALTER TABLE custodiados VALIDATE CONSTRAINT chk_custodiado_motivo_arq;

ALTER TABLE processos
    ADD CONSTRAINT chk_processo_natureza
    CHECK (natureza_vinculo IN ('CAUTELAR','EXECUCAO_REGIME_ABERTO')) NOT VALID;
ALTER TABLE processos VALIDATE CONSTRAINT chk_processo_natureza;

ALTER TABLE processos
    ADD CONSTRAINT chk_processo_situacao
    CHECK (situacao_processo IN ('ATIVO','ENCERRADO','SUSPENSO')) NOT VALID;
ALTER TABLE processos VALIDATE CONSTRAINT chk_processo_situacao;

ALTER TABLE execucoes_penais
    ADD CONSTRAINT chk_execucao_situacao
    CHECK (situacao_execucao IN ('EM_CUMPRIMENTO','AGUARDANDO_JUSTIFICACAO',
        'SUSPENSA','REGREDIDA','EXTINTA')) NOT VALID;
ALTER TABLE execucoes_penais VALIDATE CONSTRAINT chk_execucao_situacao;

ALTER TABLE incidentes_execucao
    ADD CONSTRAINT chk_incidente_resultado
    CHECK (resultado IN ('AGUARDANDO','JUSTIFICADO','NAO_JUSTIFICADO','ADVERTENCIA',
        'REGRESSAO_DECRETADA','EXTINCAO_DECLARADA','ARQUIVADO')) NOT VALID;
ALTER TABLE incidentes_execucao VALIDATE CONSTRAINT chk_incidente_resultado;

ALTER TABLE historico_comparecimentos
    ADD CONSTRAINT chk_historico_tipo_validacao
    CHECK (tipo_validacao IN ('PRESENCIAL','ONLINE','CADASTRO_INICIAL',
        'FALTA_JUSTIFICADA')) NOT VALID;
ALTER TABLE historico_comparecimentos VALIDATE CONSTRAINT chk_historico_tipo_validacao;

-- Unico FK realmente ausente (pos-criacao do usuario, direcao possivel).
ALTER TABLE pre_cadastros
    ADD CONSTRAINT fk_pre_cadastro_usuario
    FOREIGN KEY (usuario_criado_id) REFERENCES usuarios (id) ON DELETE SET NULL;
