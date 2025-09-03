package br.jus.tjba.aclp.model.enums;

public enum TipoUsuario {
    ADMIN("admin", "Administrador", "Acesso completo ao sistema"),
    USUARIO("usuario", "Usuário", "Acesso limitado para consulta e registro de comparecimentos");

    private final String code;
    private final String label;
    private final String description;

    TipoUsuario(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static TipoUsuario fromString(String value) {
        if (value == null) return null;

        for (TipoUsuario tipo : TipoUsuario.values()) {
            if (tipo.code.equalsIgnoreCase(value) || tipo.name().equalsIgnoreCase(value)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de usuário inválido: " + value);
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean isUsuario() {
        return this == USUARIO;
    }

    @Override
    public String toString() {
        return label;
    }
}