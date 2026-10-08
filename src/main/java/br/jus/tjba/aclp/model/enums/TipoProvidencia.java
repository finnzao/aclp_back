package br.jus.tjba.aclp.model.enums;

public enum TipoProvidencia {

    AUDIENCIA_ADMONITORIA("Marcar audiência admonitória"),
    EXPEDIR_MANDADO("Expedir mandado"),
    EXPEDIR_ATESTADO_PENA("Expedir atestado de pena"),
    CERTIFICAR("Certificar"),
    OFICIAR("Oficiar"),
    INTIMAR("Intimar"),
    VISTAS_MP("Vistas ao Ministério Público"),
    COMUNICAR_TRE("Comunicar ao TRE"),
    ARQUIVAR("Arquivar"),
    OUTRO("Outro");

    private final String descricao;

    TipoProvidencia(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
