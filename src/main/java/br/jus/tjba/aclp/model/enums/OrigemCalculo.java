package br.jus.tjba.aclp.model.enums;

/**
 * Como a pena entrou no sistema (decisão E1, respondida pelo fluxo de dois cards).
 *
 * SENTENCA             — a sentença já traz a data final; o sistema deriva a pena em dias.
 * DOSIMETRIA_CALCULADA — a sentença traz o tempo (anos/meses/dias); o sistema deriva a data final.
 */
public enum OrigemCalculo {

    SENTENCA("Data final informada na sentença"),
    DOSIMETRIA_CALCULADA("Tempo de pena informado (anos/meses/dias)");

    private final String descricao;

    OrigemCalculo(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
