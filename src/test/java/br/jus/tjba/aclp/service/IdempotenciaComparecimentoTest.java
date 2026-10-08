package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Duplo-clique/retry não pode duplicar registro judicial (M1 do review). */
class IdempotenciaComparecimentoTest {

    @Test
    void segundoRegistroMesmoProcessoDataETipoEhRejeitado() {
        var historicoRepo = mock(HistoricoComparecimentoRepository.class);
        var processoRepo = mock(ProcessoRepository.class);
        var service = new ComparecimentoService(
                mock(br.jus.tjba.aclp.repository.PessoaMonitoradaRepository.class),
                historicoRepo,
                mock(br.jus.tjba.aclp.repository.HistoricoEnderecoRepository.class),
                processoRepo,
                mock(br.jus.tjba.aclp.repository.ExecucaoPenalRepository.class),
                mock(br.jus.tjba.aclp.repository.ProvidenciaRepository.class));

        PessoaMonitorada pessoa = PessoaMonitorada.builder().nome("Teste").build();
        pessoa.setId(1L);
        Processo processo = Processo.builder()
                .numeroProcesso("0000001-00.2026.8.05.0001").custodiado(pessoa).build();
        processo.setId(10L);

        when(processoRepo.findByIdComPessoaMonitorada(10L)).thenReturn(Optional.of(processo));
        when(historicoRepo.existsByProcessoIdAndDataComparecimentoAndTipoValidacao(
                10L, LocalDate.of(2026, 9, 7), TipoValidacao.PRESENCIAL)).thenReturn(true);

        ComparecimentoDTO dto = ComparecimentoDTO.builder()
                .processoId(10L)
                .dataComparecimento(LocalDate.of(2026, 9, 7))
                .tipoValidacao(TipoValidacao.PRESENCIAL)
                .validadoPor("Servidor Teste")
                .build();

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> service.registrarComparecimento(dto));
        assertTrue(e.getMessage().contains("Já existe comparecimento"));
        verify(historicoRepo, never()).save(any());
    }
}
