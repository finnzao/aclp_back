package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 1 da extensão para regime aberto: o discriminador cautelar × execução de pena.
 *
 * O contrato desta fase é "não mudar nada do que existe". O que precisa ficar travado
 * por teste é justamente isso — que um processo criado sem informar a natureza continue
 * se comportando como medida cautelar, e que o cálculo de comparecimento seja idêntico
 * nas duas naturezas.
 */
@DisplayName("Natureza do vínculo — cautelar × execução de pena")
class NaturezaVinculoTest {

    private Processo processoCom(NaturezaVinculo natureza) {
        Processo p = Processo.builder()
                .numeroProcesso("0000001-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.now().minusMonths(2))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.now().minusDays(5))
                .situacaoProcesso(SituacaoProcesso.ATIVO)
                .naturezaVinculo(natureza)
                .build();
        p.prePersist();
        return p;
    }

    @Test
    @DisplayName("Processo sem natureza informada nasce CAUTELAR")
    void defaultEhCautelar() {
        // Espelha as linhas já existentes no banco: a coluna tem DEFAULT 'CAUTELAR',
        // então nenhum processo cadastrado antes desta fase muda de comportamento.
        Processo semNatureza = Processo.builder()
                .numeroProcesso("0000002-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.now().minusMonths(2))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.now().minusDays(5))
                .build();

        assertEquals(NaturezaVinculo.CAUTELAR, semNatureza.getNaturezaVinculo());
        assertFalse(semNatureza.isExecucao());
    }

    @Test
    @DisplayName("prePersist não sobrescreve natureza já informada")
    void prePersistPreservaNaturezaInformada() {
        Processo execucao = processoCom(NaturezaVinculo.EXECUCAO_REGIME_ABERTO);

        assertEquals(NaturezaVinculo.EXECUCAO_REGIME_ABERTO, execucao.getNaturezaVinculo());
        assertTrue(execucao.isExecucao());
    }

    @Test
    @DisplayName("prePersist repõe CAUTELAR se a natureza vier nula")
    void prePersistRepoeNaturezaNula() {
        Processo p = processoCom(null);

        assertEquals(NaturezaVinculo.CAUTELAR, p.getNaturezaVinculo());
    }

    @Test
    @DisplayName("Cálculo de comparecimento é idêntico nas duas naturezas")
    void comparecimentoNaoMudaComNatureza() {
        // O ganho de estender em vez de duplicar o modelo é exatamente este: a
        // execução de pena reaproveita o ciclo de comparecimento sem bifurcação.
        Processo cautelar = processoCom(NaturezaVinculo.CAUTELAR);
        Processo execucao = processoCom(NaturezaVinculo.EXECUCAO_REGIME_ABERTO);

        assertEquals(cautelar.getProximoComparecimento(), execucao.getProximoComparecimento());
        assertEquals(cautelar.getStatus(), execucao.getStatus());
        assertEquals(cautelar.isInadimplente(), execucao.isInadimplente());
        assertEquals(cautelar.getDiasAtraso(), execucao.getDiasAtraso());
    }

    @Test
    @DisplayName("isExecucao concentra a decisão de natureza num ponto só")
    void isExecucaoDistingueAsDuas() {
        assertTrue(processoCom(NaturezaVinculo.EXECUCAO_REGIME_ABERTO).isExecucao());
        assertFalse(processoCom(NaturezaVinculo.CAUTELAR).isExecucao());
    }
}
