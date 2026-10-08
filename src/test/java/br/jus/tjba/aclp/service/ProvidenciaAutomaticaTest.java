package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RegistroPenaRequest;
import br.jus.tjba.aclp.model.ExecucaoPenal;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.Providencia;
import br.jus.tjba.aclp.model.enums.ModalidadeExecucao;
import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.TipoProvidencia;
import br.jus.tjba.aclp.repository.ExecucaoPenalRepository;
import br.jus.tjba.aclp.repository.IncidenteExecucaoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import br.jus.tjba.aclp.repository.ProvidenciaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class ProvidenciaAutomaticaTest {

    private final ExecucaoPenalRepository execRepo = mock(ExecucaoPenalRepository.class);
    private final ProvidenciaRepository provRepo = mock(ProvidenciaRepository.class);
    private final ProcessoRepository procRepo = mock(ProcessoRepository.class);
    private final ExecucaoPenalService service = new ExecucaoPenalService(
            execRepo, mock(IncidenteExecucaoRepository.class), provRepo, procRepo);

    private Processo processo() {
        PessoaMonitorada p = PessoaMonitorada.builder().nome("Teste").build();
        Processo pr = Processo.builder().numeroProcesso("0000001-00.2026.8.05.0001")
                .custodiado(p).naturezaVinculo(NaturezaVinculo.EXECUCAO_REGIME_ABERTO)
                .periodicidade(30).proximoComparecimento(LocalDate.of(2026, 11, 1)).build();
        pr.setId(10L);
        when(procRepo.findById(10L)).thenReturn(Optional.of(pr));
        when(execRepo.save(any())).thenAnswer(i -> { ExecucaoPenal e = i.getArgument(0); e.setId(5L); return e; });
        when(provRepo.ultimaMovimentacaoPorExecucao(anyCollection())).thenReturn(List.of());
        return pr;
    }

    private RegistroPenaRequest req(ModalidadeExecucao m) {
        return RegistroPenaRequest.builder().processoId(10L).modalidade(m)
                .numeroExecucao("0000123-45.2026.8.05.0001").dataInicio(LocalDate.of(2026, 1, 1))
                .origemCalculo(br.jus.tjba.aclp.model.enums.OrigemCalculo.DOSIMETRIA_CALCULADA).anos(2).build();
    }

    @Test
    void abertoAbreAudienciaAdmonitoriaEMantemAgenda() {
        Processo pr = processo();
        service.registrarPena(req(ModalidadeExecucao.ABERTO));
        ArgumentCaptor<Providencia> cap = ArgumentCaptor.forClass(Providencia.class);
        verify(provRepo).save(cap.capture());
        assertEquals(TipoProvidencia.AUDIENCIA_ADMONITORIA, cap.getValue().getTipo());
        assertNotNull(pr.getProximoComparecimento());
    }

    @Test
    void ambulatorialSemPenaSemAgendaComLaudo() {
        Processo pr = processo();
        var r = service.registrarPena(req(ModalidadeExecucao.TRATAMENTO_AMBULATORIAL));
        assertNull(r.getPenaTotalDias());
        assertNull(pr.getProximoComparecimento());
        ArgumentCaptor<Providencia> cap = ArgumentCaptor.forClass(Providencia.class);
        verify(provRepo).save(cap.capture());
        assertEquals(TipoProvidencia.OFICIAR, cap.getValue().getTipo());
    }

    @Test
    void fechadoNaoAbreProvidenciaNemAgenda() {
        Processo pr = processo();
        service.registrarPena(req(ModalidadeExecucao.FECHADO));
        verify(provRepo, never()).save(any());
        assertNull(pr.getProximoComparecimento());
    }
}
