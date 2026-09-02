package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Situação do custodiado no sistema
 * Define se o custodiado está ativo (em observação) ou arquivado
 */
@Schema(description = "Situação do custodiado no sistema")
public enum SituacaoPessoaMonitorada {

    @Schema(description = "Pessoa monitorada ativa, deve comparecer regularmente")
    ATIVO("ATIVO", "Ativo", "Pessoa monitorada em acompanhamento ativa"),

    @Schema(description = "Pessoa monitorada arquivada, não precisa mais comparecer")
    ARQUIVADO("ARQUIVADO", "Arquivado", "Pessoa monitorada arquivada - fora de observação"),

    @Schema(description = "Pessoa monitorada foragida - declarada por decisão, nunca automático")
    FORAGIDO("FORAGIDO", "Foragido", "Paradeiro desconhecido; fora dos contadores de atraso");

    private final String code;
    private final String label;
    private final String description;

    SituacaoPessoaMonitorada(String code, String label, String description) {
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
     * Verifica se o custodiado está ativo (deve comparecer)
     */
    public boolean isAtivo() {
        return this == ATIVO;
    }

    /**
     * Verifica se o custodiado está arquivado (não precisa comparecer)
     */
    public boolean isArquivado() {
        return this == ARQUIVADO;
    }

    public static SituacaoPessoaMonitorada fromString(String value) {
        if (value == null) return null;

        for (SituacaoPessoaMonitorada situacao : SituacaoPessoaMonitorada.values()) {
            if (situacao.code.equalsIgnoreCase(value) || situacao.name().equalsIgnoreCase(value)) {
                return situacao;
            }
        }
        throw new IllegalArgumentException("Situação de custodiado inválida: " + value + ". Use ATIVO, ARQUIVADO ou FORAGIDO");
    }

    public String getCssClass() {
        return switch (this) {
            case ATIVO -> "success";
            case FORAGIDO -> "danger";
            case ARQUIVADO -> "secondary";
        };
    }

    @Override
    public String toString() {
        return code;
    }
}