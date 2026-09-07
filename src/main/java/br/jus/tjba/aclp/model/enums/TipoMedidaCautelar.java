package br.jus.tjba.aclp.model.enums;

import lombok.Getter;

/** Medidas do art. 319 do CPP; OUTRA cobre condicoes atipicas e as do regime aberto. */
@Getter
public enum TipoMedidaCautelar {

    COMPARECIMENTO_PERIODICO("Comparecimento periódico em juízo", "art. 319, I"),
    PROIBICAO_FREQUENTAR_LUGARES("Proibição de frequentar determinados lugares", "art. 319, II"),
    PROIBICAO_CONTATO("Proibição de contato com pessoa determinada", "art. 319, III"),
    PROIBICAO_AUSENTAR_COMARCA("Proibição de ausentar-se da comarca", "art. 319, IV"),
    RECOLHIMENTO_NOTURNO("Recolhimento domiciliar noturno", "art. 319, V"),
    SUSPENSAO_FUNCAO("Suspensão de função pública ou atividade", "art. 319, VI"),
    INTERNACAO_PROVISORIA("Internação provisória", "art. 319, VII"),
    FIANCA("Fiança", "art. 319, VIII"),
    MONITORACAO_ELETRONICA("Monitoração eletrônica", "art. 319, IX"),
    OUTRA("Outra medida ou condição", "—");

    private final String descricao;
    private final String baseLegal;

    TipoMedidaCautelar(String descricao, String baseLegal) {
        this.descricao = descricao;
        this.baseLegal = baseLegal;
    }
}
