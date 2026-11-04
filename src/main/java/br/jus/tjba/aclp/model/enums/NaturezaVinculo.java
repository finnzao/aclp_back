package br.jus.tjba.aclp.model.enums;

/**
 * Natureza do vínculo do processo com o ACLP.
 *
 * CAUTELAR                — medida cautelar de comparecimento periódico (CPP art. 319, IV)
 * EXECUCAO_PENAL  — execução de pena, qualquer modalidade (LEP arts. 113-119)
 */
public enum NaturezaVinculo {

    CAUTELAR("Medida cautelar", "CPP art. 319, IV"),
    EXECUCAO_PENAL("Execução de pena", "LEP arts. 113-119");

    private final String descricao;
    private final String baseLegal;

    NaturezaVinculo(String descricao, String baseLegal) {
        this.descricao = descricao;
        this.baseLegal = baseLegal;
    }

    public String getDescricao() { return descricao; }
    public String getBaseLegal() { return baseLegal; }
}
