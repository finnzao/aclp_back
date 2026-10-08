package br.jus.tjba.aclp.model.enums;

public enum ModalidadeExecucao {

    ABERTO("Regime aberto", true, true),
    SEMIABERTO("Regime semiaberto", false, true),
    FECHADO("Regime fechado", false, true),
    LIVRAMENTO_CONDICIONAL("Livramento condicional", true, true),
    PRD("Pena restritiva de direitos", false, true),
    TRATAMENTO_AMBULATORIAL("Tratamento ambulatorial", false, false);

    private final String descricao;
    private final boolean comparecimentoPeriodico;
    private final boolean terminoCalculavel;

    ModalidadeExecucao(String descricao, boolean comparecimentoPeriodico, boolean terminoCalculavel) {
        this.descricao = descricao;
        this.comparecimentoPeriodico = comparecimentoPeriodico;
        this.terminoCalculavel = terminoCalculavel;
    }

    public String getDescricao() { return descricao; }

    public boolean isComparecimentoPeriodico() { return comparecimentoPeriodico; }

    public boolean isTerminoCalculavel() { return terminoCalculavel; }
}
