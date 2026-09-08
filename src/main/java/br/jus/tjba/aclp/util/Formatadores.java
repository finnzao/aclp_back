package br.jus.tjba.aclp.util;

/** Formatação compartilhada entre cadastro e buscas. */
public final class Formatadores {

    private Formatadores() {}

    public static String formatarProcesso(String p) {
        String n = p.replaceAll("[^\\d]", "");
        if (n.length() != 20) return p.trim();
        return String.format("%s-%s.%s.%s.%s.%s",
                n.substring(0, 7), n.substring(7, 9), n.substring(9, 13),
                n.substring(13, 14), n.substring(14, 16), n.substring(16, 20));
    }
}
