package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoValidacao {
    PRESENCIAL("presencial", "Presencial", "Comparecimento físico no local"),
    ONLINE("online", "Online/Virtual", "Comparecimento virtual/remoto"),
    CADASTRO_INICIAL("cadastro_inicial", "Cadastro Inicial", "Primeiro registro no sistema");

    private final String code;
    private final String label;
    private final String description;

    TipoValidacao(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Aceita tanto o código quanto o nome do enum
     */
    @JsonCreator
    public static TipoValidacao fromString(String value) {
        if (value == null) return null;

        // Primeiro tenta pelo código (minúsculas)
        for (TipoValidacao tipo : TipoValidacao.values()) {
            if (tipo.code.equalsIgnoreCase(value)) {
                return tipo;
            }
        }

        // Depois tenta pelo nome do enum (maiúsculas)
        for (TipoValidacao tipo : TipoValidacao.values()) {
            if (tipo.name().equalsIgnoreCase(value)) {
                return tipo;
            }
        }

        throw new IllegalArgumentException(
                String.format("Tipo de validação inválido: '%s'. Use: presencial, online ou cadastro_inicial", value)
        );
    }

    public boolean requerPresencaFisica() {
        return this == PRESENCIAL;
    }

    public boolean isVirtual() {
        return this == ONLINE;
    }

    public boolean isCadastroInicial() {
        return this == CADASTRO_INICIAL;
    }

    public boolean isComparecimentoRegular() {
        return this == PRESENCIAL || this == ONLINE;
    }

    public String getIcon() {
        return switch (this) {
            case PRESENCIAL -> "building";
            case ONLINE -> "monitor";
            case CADASTRO_INICIAL -> "user-plus";
        };
    }

    public String getCssClass() {
        return switch (this) {
            case PRESENCIAL -> "success";
            case ONLINE -> "info";
            case CADASTRO_INICIAL -> "primary";
        };
    }

    @Override
    public String toString() {
        return label;
    }
}