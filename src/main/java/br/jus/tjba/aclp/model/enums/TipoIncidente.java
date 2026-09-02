package br.jus.tjba.aclp.model.enums;

public enum TipoIncidente {

    DESCUMPRIMENTO_COMPARECIMENTO("Descumprimento de comparecimento", true),
    MUDANCA_NAO_AUTORIZADA("Mudança de endereço não autorizada", true),
    NOVO_CRIME("Notícia de novo crime", true),
    EVASAO("Evasão — declarado foragido", true),
    PEDIDO_AUTORIZACAO_VIAGEM("Pedido de autorização de viagem", false),
    EXTINCAO_PUNIBILIDADE("Extinção da punibilidade", false),
    OUTRO("Outro", false);

    private final String descricao;
    /** true = suspende a normalidade: execução vai para AGUARDANDO_JUSTIFICACAO. */
    private final boolean exigeJustificacao;

    TipoIncidente(String descricao, boolean exigeJustificacao) {
        this.descricao = descricao;
        this.exigeJustificacao = exigeJustificacao;
    }

    public String getDescricao() { return descricao; }
    public boolean isExigeJustificacao() { return exigeJustificacao; }
}
