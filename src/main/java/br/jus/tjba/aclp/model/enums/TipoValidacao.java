package br.jus.tjba.aclp.model.enums;

public enum TipoValidacao {
    PRESENCIAL("presencial", "Presencial", "Comparecimento físico no local"),
    ONLINE("online", "Online", "Comparecimento virtual/remoto"),
    JUSTIFICADO("justificado", "Justificado", "Ausência justificada documentalmente");

    private final String code;
    private final String label;
    private final String description;

    TipoValidacao(String code, String label, String description) {
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

    public static TipoValidacao fromString(String value) {
        if (value == null) return null;

        for (TipoValidacao tipo : TipoValidacao.values()) {
            if (tipo.code.equalsIgnoreCase(value) || tipo.name().equalsIgnoreCase(value)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de validação inválido: " + value);
    }

    public boolean requerPresencaFisica() {
        return this == PRESENCIAL;
    }

    public boolean isVirtual() {
        return this == ONLINE;
    }

    public boolean isJustificativa() {
        return this == JUSTIFICADO;
    }

    public String getIcon() {
        return switch (this) {
            case PRESENCIAL -> "building";
            case ONLINE -> "monitor";
            case JUSTIFICADO -> "file-text";
        };
    }

    @Override
    public String toString() {
        return label;
    }
}