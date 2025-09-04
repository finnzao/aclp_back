package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status do comparecimento da pessoa", example = "EM_CONFORMIDADE")
public enum StatusComparecimento {

    @Schema(description = "Pessoa está cumprindo as obrigações regularmente")
    EM_CONFORMIDADE("EM_CONFORMIDADE", "Em Conformidade", "Pessoa está cumprindo as obrigações"),

    @Schema(description = "Pessoa não está cumprindo as obrigações")
    INADIMPLENTE("INADIMPLENTE", "Inadimplente", "Pessoa não está cumprindo as obrigações");

    private final String code;
    private final String label;
    private final String description;

    StatusComparecimento(String code, String label, String description) {
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

    public static StatusComparecimento fromString(String value) {
        if (value == null) return null;

        for (StatusComparecimento status : StatusComparecimento.values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status inválido: " + value);
    }

    public boolean isEmConformidade() {
        return this == EM_CONFORMIDADE;
    }

    public boolean isInadimplente() {
        return this == INADIMPLENTE;
    }

    public String getCssClass() {
        return switch (this) {
            case EM_CONFORMIDADE -> "success";
            case INADIMPLENTE -> "danger";
        };
    }

    @Override
    public String toString() {
        return code;
    }
}