package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cobre o bug corrigido: status exibido/contado deve vir da DATA, não do campo
 * persistido (que ficava "EM_CONFORMIDADE" mesmo com o prazo vencido).
 */
class PessoaMonitoradaStatusTest {

    private PessoaMonitorada ativo(LocalDate proximo, StatusComparecimento persistido) {
        PessoaMonitorada c = PessoaMonitorada.builder()
                .nome("Teste").situacao(SituacaoPessoaMonitorada.ATIVO)
                .build();
        c.getProcessos().add(Processo.builder()
                .situacaoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ATIVO)
                .proximoComparecimento(proximo).build());
        return c;
    }

    @Test
    void prazoVencido_mesmoComCampoEmConformidade_ficaInadimplente() {
        PessoaMonitorada c = ativo(LocalDate.now().minusDays(1), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(StatusComparecimento.INADIMPLENTE, c.getStatusEfetivo());
    }

    @Test
    void prazoHoje_estaEmConformidade() {
        PessoaMonitorada c = ativo(LocalDate.now(), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(StatusComparecimento.EM_CONFORMIDADE, c.getStatusEfetivo());
        assertEquals(0, c.getDiasAtraso());
    }

    @Test
    void prazoFuturo_estaEmConformidade() {
        PessoaMonitorada c = ativo(LocalDate.now().plusDays(5), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(StatusComparecimento.EM_CONFORMIDADE, c.getStatusEfetivo());
    }

    @Test
    void diasAtraso_contaCorretamente() {
        PessoaMonitorada c = ativo(LocalDate.now().minusDays(10), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(10, c.getDiasAtraso());
    }
}
