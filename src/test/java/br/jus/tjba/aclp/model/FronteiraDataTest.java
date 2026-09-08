package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.util.TempoAtual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** B1 do review: a virada de dia agora e testavel com relogio fixo. */
class FronteiraDataTest {

    private static final LocalDate PRAZO = LocalDate.of(2026, 9, 10);

    @AfterEach
    void devolveRelogio() {
        TempoAtual.reset();
    }

    private void fixarDia(LocalDate dia) {
        TempoAtual.fixar(Clock.fixed(
                dia.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    private PessoaMonitorada pessoaComPrazo(LocalDate prazo) {
        PessoaMonitorada c = PessoaMonitorada.builder().nome("Teste").build();
        c.getProcessos().add(Processo.builder()
                .situacaoProcesso(SituacaoProcesso.ATIVO)
                .proximoComparecimento(prazo).build());
        return c;
    }

    @Test
    void noDiaDoPrazoAindaEstaEmConformidade() {
        fixarDia(PRAZO);
        PessoaMonitorada c = pessoaComPrazo(PRAZO);
        assertEquals(StatusComparecimento.EM_CONFORMIDADE, c.getStatusEfetivo());
        assertEquals(0, c.getDiasAtraso());
    }

    @Test
    void umDiaDepoisViraInadimplenteComUmDiaDeAtraso() {
        fixarDia(PRAZO.plusDays(1));
        PessoaMonitorada c = pessoaComPrazo(PRAZO);
        assertEquals(StatusComparecimento.INADIMPLENTE, c.getStatusEfetivo());
        assertEquals(1, c.getDiasAtraso());
    }
}
