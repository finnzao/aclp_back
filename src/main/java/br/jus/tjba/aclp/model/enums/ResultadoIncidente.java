package br.jus.tjba.aclp.model.enums;

public enum ResultadoIncidente {

    AGUARDANDO("Aguardando decisão"),
    JUSTIFICADO("Justificado — volta ao cumprimento normal"),
    NAO_JUSTIFICADO("Não justificado — aguarda decisão judicial"),
    ADVERTENCIA("Advertência aplicada"),
    REGRESSAO_DECRETADA("Regressão de regime decretada"),
    EXTINCAO_DECLARADA("Extinção da punibilidade declarada"),
    ARQUIVADO("Arquivado");

    private final String descricao;

    ResultadoIncidente(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
