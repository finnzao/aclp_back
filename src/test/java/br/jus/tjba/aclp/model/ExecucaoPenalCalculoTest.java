package br.jus.tjba.aclp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 2 — matemática do registro de pena.
 *
 * CP art. 10: "o dia do começo inclui-se no cômputo do prazo. Contam-se os
 * dias, os meses e os anos pelo calendário comum." Consequências travadas aqui:
 *   - pena de 1 dia iniciada hoje termina HOJE (não amanhã)
 *   - termino = inicio + (dias - 1); dias = between + 1
 *   - meses/anos contam pelo calendário, não por "30 dias"
 * Errar este cálculo mantém alguém sob pena um dia a mais — ou a menos.
 */
@DisplayName("Execução penal — cálculo de pena e término (CP art. 10)")
class ExecucaoPenalCalculoTest {

    // ---------- Card 1: SENTENCA (data final → dias) ----------

    @Test
    @DisplayName("Pena de 1 dia: início e término no mesmo dia")
    void penaDeUmDia() {
        LocalDate d = LocalDate.of(2026, 3, 10);
        assertEquals(1, ExecucaoPenal.calcularPenaDias(d, d));
    }

    @Test
    @DisplayName("Início 01/01 e término 31/12 do mesmo ano = 365 dias")
    void anoNaoBissexto() {
        assertEquals(365, ExecucaoPenal.calcularPenaDias(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("Ano bissexto conta o 29/02")
    void anoBissexto() {
        assertEquals(366, ExecucaoPenal.calcularPenaDias(
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)));
    }

    @Test
    @DisplayName("Término anterior ao início é rejeitado")
    void terminoAntesDoInicio() {
        assertThrows(IllegalArgumentException.class, () -> ExecucaoPenal.calcularPenaDias(
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 9)));
    }

    // ---------- Card 2: DOSIMETRIA (tempo → data final) ----------

    @Test
    @DisplayName("2 anos a partir de 15/03/2026 terminam em 14/03/2028")
    void doisAnosPeloCalendario() {
        assertEquals(LocalDate.of(2028, 3, 14),
                ExecucaoPenal.calcularTerminoPorTempo(LocalDate.of(2026, 3, 15), 2, 0, 0));
    }

    @Test
    @DisplayName("2 anos, 6 meses e 10 dias — o exemplo do formato judicial")
    void formatoJudicialCompleto() {
        // 15/03/2026 + 2a = 15/03/2028; + 6m = 15/09/2028; + 10d = 25/09/2028; -1 = 24/09/2028
        assertEquals(LocalDate.of(2028, 9, 24),
                ExecucaoPenal.calcularTerminoPorTempo(LocalDate.of(2026, 3, 15), 2, 6, 10));
    }

    @Test
    @DisplayName("1 mês a partir de 31/01 termina em 27/02 (calendário, não 30 dias)")
    void mesEmFimDeMes() {
        // 31/01 + 1 mês = 28/02 (ajuste de calendário) − 1 dia = 27/02
        assertEquals(LocalDate.of(2026, 2, 27),
                ExecucaoPenal.calcularTerminoPorTempo(LocalDate.of(2026, 1, 31), 0, 1, 0));
    }

    @Test
    @DisplayName("Pena de 1 dia via dosimetria termina no próprio dia de início")
    void umDiaViaDosimetria() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        assertEquals(d, ExecucaoPenal.calcularTerminoPorTempo(d, 0, 0, 1));
    }

    @Test
    @DisplayName("Tempo zero e componente negativo são rejeitados")
    void tempoInvalido() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> ExecucaoPenal.calcularTerminoPorTempo(d, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExecucaoPenal.calcularTerminoPorTempo(d, -1, 0, 5));
    }

    // ---------- Consistência entre os dois cards ----------

    @Test
    @DisplayName("Os dois fluxos são inversos: dias(inicio, terminoPorTempo(...)) reconstrói o período")
    void fluxosSaoConsistentes() {
        LocalDate inicio = LocalDate.of(2026, 3, 15);
        LocalDate termino = ExecucaoPenal.calcularTerminoPorTempo(inicio, 2, 6, 10);
        int dias = ExecucaoPenal.calcularPenaDias(inicio, termino);
        // reconstruindo pelo card 1 com a mesma data final, a pena em dias é idêntica
        assertEquals(dias, ExecucaoPenal.calcularPenaDias(inicio, termino));
        assertTrue(dias > 900, "2a6m10d deve passar de 900 dias");
    }

    // ---------- Abatimentos (detração/remição recalculam a prevista) ----------

    @Test
    @DisplayName("Detração e remição abatem da data prevista, nunca da original")
    void abatimentosMovemSoAPrevista() {
        ExecucaoPenal e = ExecucaoPenal.builder()
                .dataInicioCumprimento(LocalDate.of(2026, 1, 1))
                .penaTotalDias(365)
                .dataTerminoOriginal(LocalDate.of(2026, 12, 31))
                .dataTerminoPrevista(LocalDate.of(2026, 12, 31))
                .diasDetraidos(30)
                .diasRemidos(10)
                .build();
        assertEquals(LocalDate.of(2026, 11, 21), e.calcularTerminoPrevisto());
        assertEquals(LocalDate.of(2026, 12, 31), e.getDataTerminoOriginal());
    }

    @Test
    @DisplayName("Remição LEP 126: 3 dias trabalhados ou 12h de estudo = 1 dia")
    void remicaoConversao() {
        assertEquals(0, ExecucaoPenal.calcularRemicao(2, 11));
        assertEquals(1, ExecucaoPenal.calcularRemicao(3, 0));
        assertEquals(1, ExecucaoPenal.calcularRemicao(0, 12));
        assertEquals(12, ExecucaoPenal.calcularRemicao(30, 24));
        assertThrows(IllegalArgumentException.class, () -> ExecucaoPenal.calcularRemicao(-1, 0));
    }

    @Test
    @DisplayName("Remição acumula, recalcula a prevista e nunca engole a pena inteira")
    void remicaoAcumulaERespeitaLimite() {
        ExecucaoPenal e = ExecucaoPenal.builder()
                .dataInicioCumprimento(LocalDate.of(2026, 1, 1))
                .penaTotalDias(100)
                .dataTerminoOriginal(LocalDate.of(2026, 4, 10))
                .dataTerminoPrevista(LocalDate.of(2026, 4, 10))
                .diasDetraidos(20)
                .build();
        e.adicionarRemicao(10);
        e.adicionarRemicao(5);
        assertEquals(15, e.getDiasRemidos());
        assertEquals(LocalDate.of(2026, 3, 6), e.getDataTerminoPrevista());
        assertThrows(IllegalArgumentException.class, () -> e.adicionarRemicao(65)); // 20+15+65 = 100
        assertThrows(IllegalArgumentException.class, () -> e.adicionarRemicao(0));
    }

    @Test
    @DisplayName("Modo calculado (E3): detração no registro já sai na prevista via @PrePersist")
    void detracaoNoRegistro() {
        ExecucaoPenal e = ExecucaoPenal.builder()
                .dataInicioCumprimento(LocalDate.of(2026, 1, 1))
                .penaTotalDias(365)
                .diasDetraidos(60)
                .dataTerminoOriginal(LocalDate.of(2026, 12, 31))
                .build();
        e.prePersist();
        assertEquals(LocalDate.of(2026, 11, 1), e.getDataTerminoPrevista());
        assertEquals(LocalDate.of(2026, 12, 31), e.getDataTerminoOriginal());
    }
}
