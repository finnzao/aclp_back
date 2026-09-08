package br.jus.tjba.aclp.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Relógio do domínio (B1 do review). Entidades não recebem injeção, então o
 * Clock vive num holder estático: produção usa o relógio do sistema; testes de
 * fronteira exata (virada de dia, D-60) fixam com {@link #fixar} e devolvem
 * com {@link #reset}.
 */
public final class TempoAtual {

    private static volatile Clock clock = Clock.systemDefaultZone();

    private TempoAtual() {}

    public static LocalDate hoje() { return LocalDate.now(clock); }

    public static LocalDateTime agora() { return LocalDateTime.now(clock); }

    /** Somente testes. */
    public static void fixar(Clock fixo) { clock = fixo; }

    /** Somente testes. */
    public static void reset() { clock = Clock.systemDefaultZone(); }
}
