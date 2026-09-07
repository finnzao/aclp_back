package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoMedidaCautelar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MedidaCautelarTest {

    private MedidaCautelar medida() {
        return MedidaCautelar.builder()
                .tipoMedida(TipoMedidaCautelar.PROIBICAO_AUSENTAR_COMARCA)
                .dataImposicao(LocalDate.of(2026, 1, 10))
                .registradoPor("teste").build();
    }

    @Test
    void revogaEDeixaDeSerVigente() {
        MedidaCautelar m = medida();
        assertTrue(m.isVigente());
        m.revogar(LocalDate.of(2026, 3, 1));
        assertFalse(m.isVigente());
    }

    @Test
    void revogacaoAnteriorAImposicaoEhRejeitada() {
        assertThrows(IllegalArgumentException.class,
                () -> medida().revogar(LocalDate.of(2026, 1, 9)));
    }

    @Test
    void naoRevogaDuasVezes() {
        MedidaCautelar m = medida();
        m.revogar(LocalDate.of(2026, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> m.revogar(LocalDate.of(2026, 4, 1)));
    }
}
