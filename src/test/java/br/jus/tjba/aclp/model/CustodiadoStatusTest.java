package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cobre o bug corrigido: status exibido/contado deve vir da DATA, não do campo
 * persistido (que ficava "EM_CONFORMIDADE" mesmo com o prazo vencido).
 */
class CustodiadoStatusTest {

    private Custodiado ativo(LocalDate proximo, StatusComparecimento persistido) {
        return Custodiado.builder()
                .nome("Teste").situacao(SituacaoCustodiado.ATIVO)
                .status(persistido).proximoComparecimento(proximo).build();
    }

    @Test
    void prazoVencido_mesmoComCampoEmConformidade_ficaInadimplente() {
        Custodiado c = ativo(LocalDate.now().minusDays(1), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(StatusComparecimento.INADIMPLENTE, c.getStatusEfetivo());
    }

    @Test
    void prazoHoje_estaEmConformidade() {
        Custodiado c = ativo(LocalDate.now(), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(StatusComparecimento.EM_CONFORMIDADE, c.getStatusEfetivo());
        assertEquals(0, c.getDiasAtraso());
    }

    @Test
    void prazoFuturo_estaEmConformidade() {
        Custodiado c = ativo(LocalDate.now().plusDays(5), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(StatusComparecimento.EM_CONFORMIDADE, c.getStatusEfetivo());
    }

    @Test
    void diasAtraso_contaCorretamente() {
        Custodiado c = ativo(LocalDate.now().minusDays(10), StatusComparecimento.EM_CONFORMIDADE);
        assertEquals(10, c.getDiasAtraso());
    }
}
