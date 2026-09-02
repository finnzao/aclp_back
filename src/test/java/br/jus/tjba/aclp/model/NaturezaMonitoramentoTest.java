package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NaturezaMonitoramentoTest {

    private Processo processo(NaturezaVinculo natureza, SituacaoProcesso situacao) {
        return Processo.builder().naturezaVinculo(natureza).situacaoProcesso(situacao).build();
    }

    @Test
    void semProcessosEhCautelar() {
        assertEquals("CAUTELAR", PessoaMonitorada.builder().build().getNaturezaMonitoramento());
    }

    @Test
    void execucaoAtivaVence() {
        PessoaMonitorada c = PessoaMonitorada.builder().build();
        c.getProcessos().add(processo(NaturezaVinculo.EXECUCAO_REGIME_ABERTO, SituacaoProcesso.ATIVO));
        assertEquals("EXECUCAO", c.getNaturezaMonitoramento());
    }

    @Test
    void execucaoEncerradaNaoConta() {
        PessoaMonitorada c = PessoaMonitorada.builder().build();
        c.getProcessos().add(processo(NaturezaVinculo.EXECUCAO_REGIME_ABERTO, SituacaoProcesso.ENCERRADO));
        c.getProcessos().add(processo(NaturezaVinculo.CAUTELAR, SituacaoProcesso.ATIVO));
        assertEquals("CAUTELAR", c.getNaturezaMonitoramento());
    }

    @Test
    void ambasAtivasEhMisto() {
        PessoaMonitorada c = PessoaMonitorada.builder().build();
        c.getProcessos().add(processo(NaturezaVinculo.CAUTELAR, SituacaoProcesso.ATIVO));
        c.getProcessos().add(processo(NaturezaVinculo.EXECUCAO_REGIME_ABERTO, SituacaoProcesso.ATIVO));
        assertEquals("MISTO", c.getNaturezaMonitoramento());
    }
}
