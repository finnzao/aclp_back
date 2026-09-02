package br.jus.tjba.aclp.model.enums;

public enum MotivoArquivamento {

    OBITO_COMPROVADO("Óbito comprovado"),
    PRISAO_OUTRO_PROCESSO("Prisão por outro processo"),
    DECISAO_JUDICIAL("Decisão judicial"),
    EXTINCAO_PUNIBILIDADE("Extinção da punibilidade"),
    OUTRO("Outro");

    private final String descricao;

    MotivoArquivamento(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
