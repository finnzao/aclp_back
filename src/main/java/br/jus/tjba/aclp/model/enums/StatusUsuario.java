package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status do usuário no sistema
 */
public enum StatusUsuario {

    INVITED("INVITED", "Convidado", "Usuário convidado aguardando primeiro acesso"),
    ACTIVE("ACTIVE", "Ativo", "Usuário ativo com acesso completo"),
    INACTIVE("INACTIVE", "Inativo", "Usuário temporariamente inativo"),
    BLOCKED("BLOCKED", "Bloqueado", "Usuário bloqueado por segurança"),
    EXPIRED("EXPIRED", "Expirado", "Convite expirado sem ativação");

    private final String code;
    private final String label;
    private final String description;

    StatusUsuario(String code, String label, String description) {
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

    public boolean isInvited() {
        return this == INVITED;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean canLogin() {
        return this == ACTIVE;
    }

    public static StatusUsuario fromString(String value) {
        if (value == null) return null;

        for (StatusUsuario status : StatusUsuario.values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de usuário inválido: " + value);
    }
}