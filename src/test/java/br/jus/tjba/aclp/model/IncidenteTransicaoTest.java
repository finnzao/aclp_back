package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.ResultadoIncidente;
import br.jus.tjba.aclp.model.enums.SituacaoExecucao;
import br.jus.tjba.aclp.model.enums.TipoIncidente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rito da LEP art. 118 §2º: regressão exige incidente com decisão registrada.
 * A única porta para REGREDIDA/EXTINTA é aoDecidirIncidente — errar aqui gera
 * nulidade em escala.
 */
@DisplayName("Incidentes — máquina de estados da execução")
class IncidenteTransicaoTest {

    private ExecucaoPenal execucao() {
        return ExecucaoPenal.builder()
                .dataInicioCumprimento(LocalDate.now().minusMonths(6))
                .penaTotalDias(730)
                .dataTerminoOriginal(LocalDate.now().plusMonths(18))
                .dataTerminoPrevista(LocalDate.now().plusMonths(18))
                .build();
    }

    @Test
    @DisplayName("Descumprimento suspende a normalidade (AGUARDANDO_JUSTIFICACAO)")
    void descumprimentoSuspende() {
        ExecucaoPenal e = execucao();
        e.aoRegistrarIncidente(TipoIncidente.DESCUMPRIMENTO_COMPARECIMENTO);
        assertEquals(SituacaoExecucao.AGUARDANDO_JUSTIFICACAO, e.getSituacaoExecucao());
    }

    @Test
    @DisplayName("Pedido de viagem NÃO suspende")
    void viagemNaoSuspende() {
        ExecucaoPenal e = execucao();
        e.aoRegistrarIncidente(TipoIncidente.PEDIDO_AUTORIZACAO_VIAGEM);
        assertEquals(SituacaoExecucao.EM_CUMPRIMENTO, e.getSituacaoExecucao());
    }

    @Test
    @DisplayName("Justificado volta ao cumprimento normal")
    void justificadoVolta() {
        ExecucaoPenal e = execucao();
        e.aoRegistrarIncidente(TipoIncidente.DESCUMPRIMENTO_COMPARECIMENTO);
        e.aoDecidirIncidente(ResultadoIncidente.JUSTIFICADO);
        assertEquals(SituacaoExecucao.EM_CUMPRIMENTO, e.getSituacaoExecucao());
    }

    @Test
    @DisplayName("Não justificado mantém aguardando — regressão exige decisão explícita")
    void naoJustificadoMantem() {
        ExecucaoPenal e = execucao();
        e.aoRegistrarIncidente(TipoIncidente.NOVO_CRIME);
        e.aoDecidirIncidente(ResultadoIncidente.NAO_JUSTIFICADO);
        assertEquals(SituacaoExecucao.AGUARDANDO_JUSTIFICACAO, e.getSituacaoExecucao());
    }

    @Test
    @DisplayName("Regressão decretada → REGREDIDA")
    void regressaoDecretada() {
        ExecucaoPenal e = execucao();
        e.aoRegistrarIncidente(TipoIncidente.DESCUMPRIMENTO_COMPARECIMENTO);
        e.aoDecidirIncidente(ResultadoIncidente.REGRESSAO_DECRETADA);
        assertEquals(SituacaoExecucao.REGREDIDA, e.getSituacaoExecucao());
    }

    @Test
    @DisplayName("Extinção declarada → EXTINTA")
    void extincaoDeclarada() {
        ExecucaoPenal e = execucao();
        e.aoDecidirIncidente(ResultadoIncidente.EXTINCAO_DECLARADA);
        assertEquals(SituacaoExecucao.EXTINTA, e.getSituacaoExecucao());
    }

    @Test
    @DisplayName("Regressão suspende o processo: quem foi preso não é mais cobrado")
    void regressaoEncerraCobranca() {
        Processo p = Processo.builder().periodicidade(30)
                .situacaoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ATIVO)
                .proximoComparecimento(LocalDate.now().plusDays(10)).build();
        ExecucaoPenal e = execucao();
        e.setProcesso(p);
        e.aoDecidirIncidente(ResultadoIncidente.REGRESSAO_DECRETADA);
        assertEquals(br.jus.tjba.aclp.model.enums.SituacaoProcesso.SUSPENSO, p.getSituacaoProcesso());
        assertNull(p.getProximoComparecimento());
    }

    @Test
    @DisplayName("Extinção encerra o processo: pena quitada não é mais cobrada")
    void extincaoEncerraCobranca() {
        Processo p = Processo.builder().periodicidade(30)
                .situacaoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ATIVO)
                .proximoComparecimento(LocalDate.now().plusDays(10)).build();
        ExecucaoPenal e = execucao();
        e.setProcesso(p);
        e.aoDecidirIncidente(ResultadoIncidente.EXTINCAO_DECLARADA);
        assertEquals(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ENCERRADO, p.getSituacaoProcesso());
        assertNull(p.getProximoComparecimento());
    }

    @Test
    @DisplayName("Falta justificada adia o ciclo sem contar presença")
    void faltaJustificadaAdiaSemPresenca() {
        LocalDate ultimo = LocalDate.now().minusDays(40);
        Processo p = Processo.builder().periodicidade(30)
                .situacaoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ATIVO)
                .ultimoComparecimento(ultimo)
                .proximoComparecimento(LocalDate.now().minusDays(10)).build();
        LocalDate dataFalta = LocalDate.now().minusDays(10);
        p.registrarFaltaJustificada(dataFalta);
        assertEquals(dataFalta.plusDays(30), p.getProximoComparecimento());
        assertEquals(ultimo, p.getUltimoComparecimento()); // presença NAO contada
        assertEquals(br.jus.tjba.aclp.model.enums.StatusComparecimento.EM_CONFORMIDADE, p.getStatus());
    }

    @Test
    @DisplayName("Advertência e arquivamento devolvem ao cumprimento")
    void advertenciaEArquivamentoVoltam() {
        for (ResultadoIncidente r : new ResultadoIncidente[]{
                ResultadoIncidente.ADVERTENCIA, ResultadoIncidente.ARQUIVADO}) {
            ExecucaoPenal e = execucao();
            e.aoRegistrarIncidente(TipoIncidente.MUDANCA_NAO_AUTORIZADA);
            e.aoDecidirIncidente(r);
            assertEquals(SituacaoExecucao.EM_CUMPRIMENTO, e.getSituacaoExecucao(), r.name());
        }
    }
}
