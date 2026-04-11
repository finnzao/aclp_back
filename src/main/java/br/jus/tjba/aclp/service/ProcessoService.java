package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ContadoresDashboardDTO;
import br.jus.tjba.aclp.dto.ProcessoDTO;
import br.jus.tjba.aclp.dto.ProcessoResumoDTO;
import br.jus.tjba.aclp.dto.ProcessoResponseDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
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
    private final CustodiadoRepository custodiadoRepository;

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
        return ProcessoResponseDTO.fromEntity(processoRepository.findByIdComCustodiado(id)
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
                .map(p -> processoRepository.findByIdComCustodiado(p.getId())
                        .map(ProcessoResponseDTO::fromEntity).orElse(ProcessoResponseDTO.fromEntity(p)));
    }

    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarInadimplentes() {
        return processoRepository.findInadimplentesComCustodiado().stream()
                .map(ProcessoResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarComparecimentosHoje() {
        return processoRepository.findComparecimentosHojeComCustodiado().stream()
                .map(ProcessoResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContadoresDashboardDTO contadoresParaDashboard() {
        return ContadoresDashboardDTO.builder()
                .totalProcessosAtivos(processoRepository.countBySituacaoProcesso(SituacaoProcesso.ATIVO))
                .emConformidade(processoRepository.countByStatusAtivo(StatusComparecimento.EM_CONFORMIDADE))
                .inadimplentes(processoRepository.countByStatusAtivo(StatusComparecimento.INADIMPLENTE))
                .comparecimentosHoje(processoRepository.findComparecimentosHojeComCustodiado().size())
                .dataConsulta(LocalDate.now())
                .build();
    }

    @Transactional
    public ProcessoResponseDTO criarProcesso(ProcessoDTO dto) {
        Custodiado custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado: " + dto.getCustodiadoId()));

        Processo processo = Processo.builder()
                .custodiado(custodiado).numeroProcesso(dto.getNumeroProcesso())
                .vara(dto.getVara().trim()).comarca(dto.getComarca().trim())
                .dataDecisao(dto.getDataDecisao()).periodicidade(dto.getPeriodicidade())
                .dataComparecimentoInicial(dto.getDataComparecimentoInicial())
                .ultimoComparecimento(dto.getDataComparecimentoInicial())
                .status(StatusComparecimento.EM_CONFORMIDADE).situacaoProcesso(SituacaoProcesso.ATIVO)
                .observacoes(dto.getObservacoes()).build();

        processo.calcularProximoComparecimento();
        return ProcessoResponseDTO.fromEntity(processoRepository.save(processo));
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
