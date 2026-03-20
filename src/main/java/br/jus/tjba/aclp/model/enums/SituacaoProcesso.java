package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Situação do processo judicial")
public enum SituacaoProcesso {

    @Schema(description = "Processo em acompanhamento ativo")
    ATIVO("ATIVO", "Ativo", "Processo em acompanhamento ativo"),

    @Schema(description = "Processo encerrado judicialmente")
    ENCERRADO("ENCERRADO", "Encerrado", "Processo encerrado judicialmente"),

    @Schema(description = "Processo temporariamente suspenso")
    SUSPENSO("SUSPENSO", "Suspenso", "Processo temporariamente suspenso");

    private final String code;
    private final String label;
    private final String description;

    SituacaoProcesso(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    @JsonValue
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }

    public boolean isAtivo() { return this == ATIVO; }
    public boolean isEncerrado() { return this == ENCERRADO; }
    public boolean isSuspenso() { return this == SUSPENSO; }

    public static SituacaoProcesso fromString(String value) {
        if (value == null) return null;
        for (SituacaoProcesso s : values()) {
            if (s.code.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Situação de processo inválida: " + value + ". Use ATIVO, ENCERRADO ou SUSPENSO");
    }

    public String getCssClass() {
        return switch (this) {
            case ATIVO -> "success";
            case ENCERRADO -> "secondary";
            case SUSPENSO -> "warning";
        };
    }

    @Override
    public String toString() { return code; }
}
