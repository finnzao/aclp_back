package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CadastroInicialDTO;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RegistroPenaRequest;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.ModalidadeExecucao;
import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.OrigemCalculo;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CadastroExecucaoFlexivelTest {

    private final PessoaMonitoradaRepository pessoaRepo = mock(PessoaMonitoradaRepository.class);
    private final HistoricoEnderecoRepository endRepo = mock(HistoricoEnderecoRepository.class);
    private final ProcessoRepository procRepo = mock(ProcessoRepository.class);
    private final ExecucaoPenalService execService = mock(ExecucaoPenalService.class);
    private final CadastroPessoaService service = new CadastroPessoaService(
            pessoaRepo, mock(HistoricoComparecimentoRepository.class), endRepo, procRepo, execService);

    private CadastroInicialDTO.CadastroInicialDTOBuilder base() {
        when(pessoaRepo.save(any())).thenAnswer(i -> { PessoaMonitorada p = i.getArgument(0); p.setId(1L); p.setPublicId(UUID.randomUUID()); return p; });
        when(procRepo.save(any())).thenAnswer(i -> { Processo p = i.getArgument(0); p.setId(10L); return p; });
        return CadastroInicialDTO.builder().nome("Pessoa Teste").rg("1234567")
                .processo("0000001-00.2026.8.05.0001").vara("1a Vara").comarca("Salvador");
    }

    private RegistroPenaRequest pena(ModalidadeExecucao m) {
        return RegistroPenaRequest.builder().modalidade(m).numeroExecucao("0000123-45.2026.8.05.0001")
                .dataInicio(LocalDate.of(2026, 1, 10)).origemCalculo(OrigemCalculo.DOSIMETRIA_CALCULADA).anos(1).build();
    }

    @Test
    void prdSemPeriodicidadeEnderecoEDecisaoCadastra() {
        var r = service.cadastroInicial(base().naturezaVinculo(NaturezaVinculo.EXECUCAO_REGIME_ABERTO)
                .pena(pena(ModalidadeExecucao.PRD)).build());
        ArgumentCaptor<Processo> cap = ArgumentCaptor.forClass(Processo.class);
        verify(procRepo).save(cap.capture());
        assertNull(cap.getValue().getPeriodicidade());
        assertEquals(LocalDate.of(2026, 1, 10), cap.getValue().getDataDecisao());
        verify(endRepo, never()).save(any(HistoricoEndereco.class));
        assertNull(r.getEnderecoCompleto());
    }

    @Test
    void abertoExigePeriodicidade() {
        var dto = base().naturezaVinculo(NaturezaVinculo.EXECUCAO_REGIME_ABERTO).pena(pena(ModalidadeExecucao.ABERTO)).build();
        assertThrows(IllegalArgumentException.class, () -> service.cadastroInicial(dto));
    }

    @Test
    void cautelarContinuaExigindoTudo() {
        var dto = base().periodicidade(30).dataDecisao(LocalDate.of(2026, 1, 1)).build();
        assertThrows(IllegalArgumentException.class, () -> service.cadastroInicial(dto));
    }
}
