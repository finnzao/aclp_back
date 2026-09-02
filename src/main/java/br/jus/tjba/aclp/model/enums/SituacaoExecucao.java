package br.jus.tjba.aclp.model.enums;

/**
 * Situação da execução penal.
 *
 * O rascunho de schema propunha "INCIDENTE_PENDENTE"; mantivemos
 * AGUARDANDO_JUSTIFICACAO (mais preciso: LEP art. 118, §2º exige audiência de
 * justificação antes de qualquer regressão) e REGREDIDA, sem a qual o desfecho
 * do art. 118 não teria representação.
 */
public enum SituacaoExecucao {

    EM_CUMPRIMENTO("Em cumprimento"),
    AGUARDANDO_JUSTIFICACAO("Aguardando audiência de justificação"),
    SUSPENSA("Suspensa por decisão judicial"),
    REGREDIDA("Regredida de regime (LEP art. 118)"),
    EXTINTA("Punibilidade extinta");

    private final String descricao;

    SituacaoExecucao(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
