package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A tendência é derivada do histórico: no prazo = ocorreu até
 * (comparecimento anterior + periodicidade).
 */
class TendenciaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 29);

    private HistoricoComparecimento comp(Custodiado c, LocalDate data) {
        return HistoricoComparecimento.builder().custodiado(c).dataComparecimento(data).build();
    }

    private Custodiado custodiado(long id, int periodicidade) {
        Custodiado c = Custodiado.builder().nome("Teste").periodicidade(periodicidade).build();
        c.setId(id);
        return c;
    }

    @Test
    void semHistorico_retornaNull() {
        assertNull(ComparecimentoService.calcularTendencia(List.of(), HOJE));
    }

    @Test
    void somenteCadastroInicial_naoGeraTendencia() {
        Custodiado c = custodiado(1L, 30);
        // um único registro não tem prazo anterior para comparar
        assertNull(ComparecimentoService.calcularTendencia(List.of(comp(c, HOJE.minusDays(5))), HOJE));
    }

    @Test
    void comparecimentoDentroDaPeriodicidade_contaComoNoPrazo() {
        Custodiado c = custodiado(1L, 30);
        var tendencia = ComparecimentoService.calcularTendencia(List.of(
                comp(c, LocalDate.of(2026, 6, 1)),
                comp(c, LocalDate.of(2026, 6, 25))), HOJE);

        var junho = tendencia.stream().filter(t -> t.getMes().equals("2026-06")).findFirst().orElseThrow();
        assertEquals(1, junho.getTotalComparecimentos());
        assertEquals(1, junho.getEmConformidade());
        assertEquals(100.0, junho.getTaxaConformidade());
    }

    @Test
    void comparecimentoAposOPrazo_contaComoAtrasado() {
        Custodiado c = custodiado(1L, 30);
        var tendencia = ComparecimentoService.calcularTendencia(List.of(
                comp(c, LocalDate.of(2026, 6, 1)),
                comp(c, LocalDate.of(2026, 7, 15))), HOJE); // esperado 01/07

        var julho = tendencia.stream().filter(t -> t.getMes().equals("2026-07")).findFirst().orElseThrow();
        assertEquals(1, julho.getTotalComparecimentos());
        assertEquals(0, julho.getEmConformidade());
        assertEquals(1, julho.getInadimplentes());
        assertEquals(100.0, julho.getTaxaInadimplencia());
    }

    @Test
    void retornaSempreSeisMesesEmOrdem() {
        Custodiado c = custodiado(1L, 30);
        var tendencia = ComparecimentoService.calcularTendencia(List.of(
                comp(c, LocalDate.of(2026, 6, 1)),
                comp(c, LocalDate.of(2026, 6, 25))), HOJE);

        assertEquals(6, tendencia.size());
        assertEquals("2026-02", tendencia.get(0).getMes());
        assertEquals("2026-07", tendencia.get(5).getMes());
    }

    @Test
    void custodiadosDiferentes_naoMisturamPrazos() {
        Custodiado a = custodiado(1L, 30);
        Custodiado b = custodiado(2L, 30);
        var tendencia = ComparecimentoService.calcularTendencia(List.of(
                comp(a, LocalDate.of(2026, 6, 1)),
                comp(b, LocalDate.of(2026, 6, 2)),
                comp(a, LocalDate.of(2026, 6, 20)),   // no prazo
                comp(b, LocalDate.of(2026, 6, 28))), HOJE); // no prazo

        var junho = tendencia.stream().filter(t -> t.getMes().equals("2026-06")).findFirst().orElseThrow();
        assertEquals(2, junho.getTotalComparecimentos());
        assertEquals(2, junho.getEmConformidade());
    }
}
