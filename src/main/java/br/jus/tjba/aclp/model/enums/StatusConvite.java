package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status possíveis de um convite
 */
public enum StatusConvite {

    PENDENTE("PENDENTE", "Pendente", "Aguardando ativação pelo usuário"),
    ATIVADO("ATIVADO", "Ativado", "Convite foi aceito e usuário criado"),
    EXPIRADO("EXPIRADO", "Expirado", "Convite expirou sem ser utilizado"),
    CANCELADO("CANCELADO", "Cancelado", "Convite foi cancelado pelo administrador");

    private final String code;
    private final String label;
    private final String description;

    StatusConvite(String code, String label, String description) {
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

    public boolean isPendente() {
        return this == PENDENTE;
    }

    public boolean isAtivado() {
        return this == ATIVADO;
    }

    public boolean isExpirado() {
        return this == EXPIRADO;
    }

    public boolean isCancelado() {
        return this == CANCELADO;
    }

    public static StatusConvite fromString(String value) {
        if (value == null) return null;

        for (StatusConvite status : StatusConvite.values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de convite inválido: " + value);
    }
}