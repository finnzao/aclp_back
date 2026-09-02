package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status do custodiado no sistema
 * Define se o custodiado está ativo (em observação) ou arquivado
 */
@Schema(description = "Status do custodiado no sistema")
public enum StatusPessoaMonitorada {

    @Schema(description = "Pessoa monitorada ativa, deve comparecer regularmente")
    ATIVO("ATIVO", "Ativo", "Pessoa monitorada em acompanhamento ativa"),

    @Schema(description = "Pessoa monitorada arquivada, não precisa mais comparecer")
    ARQUIVADO("ARQUIVADO", "Arquivado", "Pessoa monitorada arquivada - fora de observação");

    private final String code;
    private final String label;
    private final String description;

    StatusPessoaMonitorada(String code, String label, String description) {
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

    public static StatusPessoaMonitorada fromString(String value) {
        if (value == null) return null;

        for (StatusPessoaMonitorada status : StatusPessoaMonitorada.values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de custodiado inválido: " + value + ". Use ATIVO ou ARQUIVADO");
    }

    public String getCssClass() {
        return this == ATIVO ? "success" : "secondary";
    }

    @Override
    public String toString() {
        return code;
    }
}