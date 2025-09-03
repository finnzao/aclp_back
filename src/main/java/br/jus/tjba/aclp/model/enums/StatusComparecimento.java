package br.jus.tjba.aclp.model.enums;

public enum StatusComparecimento {
    EM_CONFORMIDADE("em conformidade", "Em Conformidade", "Pessoa está cumprindo as obrigações"),
    INADIMPLENTE("inadimplente", "Inadimplente", "Pessoa não está cumprindo as obrigações");

    private final String code;
    private final String label;
    private final String description;

    StatusComparecimento(String code, String label, String description) {
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
        return label;
    }
}