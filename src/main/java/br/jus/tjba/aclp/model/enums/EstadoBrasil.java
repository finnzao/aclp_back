package br.jus.tjba.aclp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum com todos os estados brasileiros e o Distrito Federal
 */
public enum EstadoBrasil {

    AC("AC", "Acre", "Norte"),
    AP("AP", "Amapá", "Norte"),
    AM("AM", "Amazonas", "Norte"),
    PA("PA", "Pará", "Norte"),
    RO("RO", "Rondônia", "Norte"),
    RR("RR", "Roraima", "Norte"),
    TO("TO", "Tocantins", "Norte"),

    AL("AL", "Alagoas", "Nordeste"),
    BA("BA", "Bahia", "Nordeste"),
    CE("CE", "Ceará", "Nordeste"),
    MA("MA", "Maranhão", "Nordeste"),
    PB("PB", "Paraíba", "Nordeste"),
    PE("PE", "Pernambuco", "Nordeste"),
    PI("PI", "Piauí", "Nordeste"),
    RN("RN", "Rio Grande do Norte", "Nordeste"),
    SE("SE", "Sergipe", "Nordeste"),

    GO("GO", "Goiás", "Centro-Oeste"),
    MT("MT", "Mato Grosso", "Centro-Oeste"),
    MS("MS", "Mato Grosso do Sul", "Centro-Oeste"),
    DF("DF", "Distrito Federal", "Centro-Oeste"),

    ES("ES", "Espírito Santo", "Sudeste"),
    MG("MG", "Minas Gerais", "Sudeste"),
    RJ("RJ", "Rio de Janeiro", "Sudeste"),
    SP("SP", "São Paulo", "Sudeste"),

    PR("PR", "Paraná", "Sul"),
    RS("RS", "Rio Grande do Sul", "Sul"),
    SC("SC", "Santa Catarina", "Sul");

    private final String sigla;
    private final String nome;
    private final String regiao;

    EstadoBrasil(String sigla, String nome, String regiao) {
        this.sigla = sigla;
        this.nome = nome;
        this.regiao = regiao;
    }

    @JsonValue
    public String getSigla() {
        return sigla;
    }

    public String getNome() {
        return nome;
    }

    public String getRegiao() {
        return regiao;
    }

    public String getNomeCompleto() {
        return nome + " (" + sigla + ")";
    }

    /**
     * Converte string para enum do estado
     * @param value Sigla ou nome do estado
     * @return EstadoBrasil correspondente
     * @throws IllegalArgumentException se o estado não for encontrado
     */
    public static EstadoBrasil fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Estado não pode ser vazio");
        }

        String valorLimpo = value.trim().toUpperCase();

        // Buscar por sigla
        for (EstadoBrasil estado : EstadoBrasil.values()) {
            if (estado.sigla.equals(valorLimpo)) {
                return estado;
            }
        }

        // Buscar por nome (case-insensitive)
        for (EstadoBrasil estado : EstadoBrasil.values()) {
            if (estado.nome.toUpperCase().equals(valorLimpo)) {
                return estado;
            }
        }

        throw new IllegalArgumentException("Estado inválido: " + value + ". Use uma sigla válida (ex: BA, SP, RJ) ou nome completo");
    }

    /**
     * Verifica se a sigla é válida
     * @param sigla Sigla a ser verificada
     * @return true se a sigla for válida
     */
    public static boolean isValidSigla(String sigla) {
        if (sigla == null || sigla.trim().isEmpty()) {
            return false;
        }

        String siglaLimpa = sigla.trim().toUpperCase();
        for (EstadoBrasil estado : EstadoBrasil.values()) {
            if (estado.sigla.equals(siglaLimpa)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retorna lista de todas as siglas válidas
     * @return String com siglas separadas por vírgula
     */
    public static String getSiglasValidas() {
        StringBuilder sb = new StringBuilder();
        for (EstadoBrasil estado : EstadoBrasil.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(estado.sigla);
        }
        return sb.toString();
    }

    /**
     * Retorna estados de uma região específica
     * @param regiao Nome da região
     * @return Array de estados da região
     */
    public static EstadoBrasil[] getEstadosPorRegiao(String regiao) {
        return java.util.Arrays.stream(EstadoBrasil.values())
                .filter(estado -> estado.regiao.equalsIgnoreCase(regiao))
                .toArray(EstadoBrasil[]::new);
    }

    @Override
    public String toString() {
        return sigla;
    }
}