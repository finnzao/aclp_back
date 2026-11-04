package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ContadoresDashboardDTO;
import br.jus.tjba.aclp.dto.ProcessoDTO;
import br.jus.tjba.aclp.dto.ProcessoResumoDTO;
import br.jus.tjba.aclp.dto.ProcessoResponseDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessoService {

    private final ProcessoRepository processoRepository;
    private final PessoaMonitoradaRepository custodiadoRepository;
    private final ExecucaoPenalService execucaoPenalService;

    /**
     * CORREÇÃO DE PERFORMANCE: Busca processos de múltiplos custodiados em uma query.
     * Substitui o padrão N+1 onde o frontend fazia GET /processos/custodiado/{id} em loop.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ProcessoResumoDTO>> buscarProcessosPorCustodiadoIds(List<Long> custodiadoIds) {
        log.info("Busca em lote para {} custodiados", custodiadoIds.size());

        List<Processo> processos = processoRepository.findByCustodiadoIdIn(custodiadoIds);

        Map<Long, List<ProcessoResumoDTO>> resultado = processos.stream()
                .map(ProcessoResumoDTO::fromEntity)
                .collect(Collectors.groupingBy(ProcessoResumoDTO::getCustodiadoId));

        // Garantir que todos IDs apareçam no resultado
        for (Long id : custodiadoIds) {
            resultado.putIfAbsent(id, new ArrayList<>());
        }

        return resultado;
    }

    @Transactional(readOnly = true)
    public Page<ProcessoResponseDTO> listarComFiltros(String termo, StatusComparecimento status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("proximoComparecimento").ascending());
        Page<Processo> pageResult = processoRepository.findComFiltros(
                (termo != null && termo.isBlank()) ? null : termo, status, pageable);
        List<ProcessoResponseDTO> dtos = pageResult.getContent().stream()
                .map(ProcessoResponseDTO::fromEntity).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, pageResult.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProcessoResponseDTO buscarPorId(Long id) {
        return ProcessoResponseDTO.fromEntity(processoRepository.findByIdComPessoaMonitorada(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + id)));
    }

    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarPorCustodiado(Long custodiadoId) {
        return processoRepository.findProcessosAtivosByCustodiado(custodiadoId).stream()
                .map(ProcessoResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ProcessoResponseDTO> buscarPorNumeroProcesso(String numero) {
        return processoRepository.findByNumeroProcesso(numero)
                .map(p -> processoRepository.findByIdComPessoaMonitorada(p.getId())
                        .map(ProcessoResponseDTO::fromEntity).orElse(ProcessoResponseDTO.fromEntity(p)));
    }

    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarInadimplentes() {
        return processoRepository.findInadimplentesComPessoaMonitorada().stream()
                .map(ProcessoResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarComparecimentosHoje() {
        return processoRepository.findComparecimentosHojeComPessoaMonitorada().stream()
                .map(ProcessoResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContadoresDashboardDTO contadoresParaDashboard() {
        return ContadoresDashboardDTO.builder()
                .totalProcessosAtivos(processoRepository.countBySituacaoProcesso(SituacaoProcesso.ATIVO))
                .emConformidade(processoRepository.countByStatusAtivo(StatusComparecimento.EM_CONFORMIDADE))
                .inadimplentes(processoRepository.countByStatusAtivo(StatusComparecimento.INADIMPLENTE))
                .comparecimentosHoje(processoRepository.findComparecimentosHojeComPessoaMonitorada().size())
                .dataConsulta(LocalDate.now())
                .build();
    }

    @Transactional
    public ProcessoResponseDTO criarProcesso(ProcessoDTO dto) {
        PessoaMonitorada custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + dto.getCustodiadoId()));

        boolean execucao = dto.getNaturezaVinculo() == NaturezaVinculo.EXECUCAO_PENAL;
        if (execucao && dto.getPena() == null)
            throw new IllegalArgumentException("Processo de execução exige o bloco 'pena' com os dados da sentença.");
        boolean temAgenda = !execucao || dto.getPena().getModalidade() == null
                || dto.getPena().getModalidade().isComparecimentoPeriodico();
        if (temAgenda && dto.getPeriodicidade() == null)
            throw new IllegalArgumentException("Periodicidade é obrigatória nesta modalidade");
        if (!execucao && dto.getDataDecisao() == null)
            throw new IllegalArgumentException("Data da decisão é obrigatória");
        LocalDate dataDecisao = dto.getDataDecisao() != null ? dto.getDataDecisao()
                : (execucao ? dto.getPena().getDataInicio() : null);
        LocalDate dataCompInicial = dto.getDataComparecimentoInicial() != null
                ? dto.getDataComparecimentoInicial() : LocalDate.now();

        Processo processo = Processo.builder()
                .custodiado(custodiado).numeroProcesso(dto.getNumeroProcesso())
                .vara(dto.getVara().trim()).comarca(dto.getComarca().trim())
                .dataDecisao(dataDecisao).periodicidade(temAgenda ? dto.getPeriodicidade() : null)
                .dataComparecimentoInicial(dataCompInicial)
                .ultimoComparecimento(dataCompInicial)
                .status(StatusComparecimento.EM_CONFORMIDADE).situacaoProcesso(SituacaoProcesso.ATIVO)
                .naturezaVinculo(execucao ? NaturezaVinculo.EXECUCAO_PENAL : NaturezaVinculo.CAUTELAR)
                .observacoes(dto.getObservacoes()).build();

        processo.calcularProximoComparecimento();
        Processo salvo = processoRepository.save(processo);
        if (execucao) {
            dto.getPena().setProcessoId(salvo.getId());
            execucaoPenalService.registrarPena(dto.getPena());
        }
        return ProcessoResponseDTO.fromEntity(salvo);
    }

    @Transactional
    public ProcessoResponseDTO atualizarProcesso(Long id, ProcessoDTO dto) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + id));
        processo.setNumeroProcesso(dto.getNumeroProcesso());
        processo.setVara(dto.getVara().trim());
        processo.setComarca(dto.getComarca().trim());
        processo.setDataDecisao(dto.getDataDecisao());
        processo.setPeriodicidade(dto.getPeriodicidade());
        processo.setDataComparecimentoInicial(dto.getDataComparecimentoInicial());
        processo.setObservacoes(dto.getObservacoes());
        processo.calcularProximoComparecimento();
        return ProcessoResponseDTO.fromEntity(processoRepository.save(processo));
    }

    @Transactional
    public ProcessoResponseDTO encerrarProcesso(Long id) {
        Processo p = processoRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + id));
        p.setSituacaoProcesso(SituacaoProcesso.ENCERRADO);
        p.setProximoComparecimento(null);
        return ProcessoResponseDTO.fromEntity(processoRepository.save(p));
    }

    @Transactional
    public ProcessoResponseDTO suspenderProcesso(Long id) {
        Processo p = processoRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + id));
        p.setSituacaoProcesso(SituacaoProcesso.SUSPENSO);
        p.setProximoComparecimento(null);
        return ProcessoResponseDTO.fromEntity(processoRepository.save(p));
    }

    @Transactional
    public ProcessoResponseDTO reativarProcesso(Long id) {
        Processo p = processoRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + id));
        p.setSituacaoProcesso(SituacaoProcesso.ATIVO);
        p.calcularProximoComparecimento();
        p.atualizarStatusBaseadoEmData();
        return ProcessoResponseDTO.fromEntity(processoRepository.save(p));
    }
}
