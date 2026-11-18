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

    private Processo cadastradoHojeSemDataReal(LocalDate hoje) {
        Processo p = Processo.builder().situacaoProcesso(SituacaoProcesso.ATIVO).periodicidade(30)
                .dataComparecimentoInicial(hoje).ultimoComparecimento(hoje).build();
        p.calcularProximoComparecimento();
        return p;
    }

    @Test
    void corrigirDataInicialSemRegistroPosteriorRecalculaPrazoEStatus() {
        LocalDate hoje = LocalDate.of(2026, 11, 18);
        fixarDia(hoje);
        Processo p = cadastradoHojeSemDataReal(hoje);

        p.corrigirComparecimentoInicial(LocalDate.of(2026, 9, 24), false);

        assertEquals(LocalDate.of(2026, 10, 24), p.getProximoComparecimento());
        assertEquals(StatusComparecimento.INADIMPLENTE, p.getStatus());
    }

    @Test
    void corrigirDataInicialComRegistroPosteriorNaoMexeNoPrazo() {
        LocalDate hoje = LocalDate.of(2026, 11, 18);
        fixarDia(hoje);
        Processo p = cadastradoHojeSemDataReal(hoje);

        p.corrigirComparecimentoInicial(LocalDate.of(2026, 9, 24), true);

        assertEquals(hoje.plusDays(30), p.getProximoComparecimento());
        assertEquals(StatusComparecimento.EM_CONFORMIDADE, p.getStatus());
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
