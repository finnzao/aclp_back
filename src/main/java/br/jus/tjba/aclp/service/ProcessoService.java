package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ContadoresDashboardDTO;
import br.jus.tjba.aclp.dto.ProcessoDTO;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ProcessoService — versão corrigida.
 *
 * CORREÇÃO CRÍTICA — LazyInitializationException:
 *   Todos os métodos que retornavam {@link Processo} (entidade) foram alterados
 *   para retornar {@link ProcessoResponseDTO}.  A conversão ocorre DENTRO da
 *   transação (@Transactional), enquanto a sessão Hibernate ainda está aberta,
 *   portanto o acesso a {@code processo.getCustodiado().getCpf()} é seguro.
 *
 *   Controladores que usavam {@code Processo} como tipo de retorno devem
 *   ser atualizados para {@code ProcessoResponseDTO}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessoService {

    private final ProcessoRepository processoRepository;
    private final CustodiadoRepository custodiadoRepository;

    // =====================================================================
    // READ
    // =====================================================================

    /**
     * Listagem paginada com filtros.
     * Retorna DTO para evitar LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public Page<ProcessoResponseDTO> listarComFiltros(String termo, StatusComparecimento status,
                                                       int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("proximoComparecimento").ascending());

        Page<Processo> pageResult = processoRepository.findComFiltros(
                (termo != null && termo.isBlank()) ? null : termo,
                status,
                pageable
        );

        // Conversão dentro da transação — sessão ainda aberta
        List<ProcessoResponseDTO> dtos = pageResult.getContent().stream()
                .map(ProcessoResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, pageResult.getTotalElements());
    }

    /**
     * Busca por ID — retorna DTO.
     */
    @Transactional(readOnly = true)
    public ProcessoResponseDTO buscarPorId(Long id) {
        Processo processo = processoRepository.findByIdComCustodiado(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo não encontrado com ID: " + id));
        return ProcessoResponseDTO.fromEntity(processo);
    }

    /**
     * Busca processos ativos de um custodiado — retorna lista de DTOs.
     */
    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarPorCustodiado(Long custodiadoId) {
        return processoRepository.findProcessosAtivosByCustodiado(custodiadoId)
                .stream()
                .map(ProcessoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Busca por número de processo — retorna DTO opcional.
     */
    @Transactional(readOnly = true)
    public Optional<ProcessoResponseDTO> buscarPorNumeroProcesso(String numero) {
        return processoRepository.findByNumeroProcesso(numero)
                .map(p -> {
                    // precisa do custodiado carregado — usar findByIdComCustodiado
                    return processoRepository.findByIdComCustodiado(p.getId())
                            .map(ProcessoResponseDTO::fromEntity)
                            .orElse(ProcessoResponseDTO.fromEntity(p));
                });
    }

    /**
     * Inadimplentes — retorna lista de DTOs.
     */
    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarInadimplentes() {
        return processoRepository.findInadimplentesComCustodiado()
                .stream()
                .map(ProcessoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Comparecimentos para hoje — retorna lista de DTOs.
     */
    @Transactional(readOnly = true)
    public List<ProcessoResponseDTO> buscarComparecimentosHoje() {
        return processoRepository.findComparecimentosHojeComCustodiado()
                .stream()
                .map(ProcessoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Contadores para o dashboard.
     */
    @Transactional(readOnly = true)
    public ContadoresDashboardDTO contadoresParaDashboard() {
        long total          = processoRepository.countBySituacaoProcesso(SituacaoProcesso.ATIVO);
        long conformidade   = processoRepository.countByStatusAtivo(StatusComparecimento.EM_CONFORMIDADE);
        long inadimplentes  = processoRepository.countByStatusAtivo(StatusComparecimento.INADIMPLENTE);
        long hoje           = processoRepository.findComparecimentosHojeComCustodiado().size();

        return ContadoresDashboardDTO.builder()
                .totalProcessosAtivos(total)
                .emConformidade(conformidade)
                .inadimplentes(inadimplentes)
                .comparecimentosHoje(hoje)
                .dataConsulta(LocalDate.now())
                .build();
    }

    // =====================================================================
    // WRITE
    // =====================================================================

    @Transactional
    public ProcessoResponseDTO criarProcesso(ProcessoDTO dto) {
        log.info("Criando processo {} para custodiado ID: {}",
                dto.getNumeroProcesso(), dto.getCustodiadoId());

        Custodiado custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Custodiado não encontrado com ID: " + dto.getCustodiadoId()));

        Processo processo = Processo.builder()
                .custodiado(custodiado)
                .numeroProcesso(dto.getNumeroProcesso())
                .vara(dto.getVara().trim())
                .comarca(dto.getComarca().trim())
                .dataDecisao(dto.getDataDecisao())
                .periodicidade(dto.getPeriodicidade())
                .dataComparecimentoInicial(dto.getDataComparecimentoInicial())
                .ultimoComparecimento(dto.getDataComparecimentoInicial())
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .situacaoProcesso(SituacaoProcesso.ATIVO)
                .observacoes(dto.getObservacoes())
                .build();

        processo.calcularProximoComparecimento();
        Processo salvo = processoRepository.save(processo);
        log.info("Processo criado — ID: {}, Número: {}", salvo.getId(), salvo.getNumeroProcesso());

        // Conversão ainda dentro da transação
        return ProcessoResponseDTO.fromEntity(salvo);
    }

    @Transactional
    public ProcessoResponseDTO atualizarProcesso(Long id, ProcessoDTO dto) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo não encontrado com ID: " + id));

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
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo não encontrado com ID: " + id));

        processo.setSituacaoProcesso(SituacaoProcesso.ENCERRADO);
        processo.setProximoComparecimento(null);
        log.info("Processo encerrado — ID: {}, Número: {}", id, processo.getNumeroProcesso());

        return ProcessoResponseDTO.fromEntity(processoRepository.save(processo));
    }

    @Transactional
    public ProcessoResponseDTO suspenderProcesso(Long id) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo não encontrado com ID: " + id));

        processo.setSituacaoProcesso(SituacaoProcesso.SUSPENSO);
        processo.setProximoComparecimento(null);

        return ProcessoResponseDTO.fromEntity(processoRepository.save(processo));
    }

    @Transactional
    public ProcessoResponseDTO reativarProcesso(Long id) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo não encontrado com ID: " + id));

        processo.setSituacaoProcesso(SituacaoProcesso.ATIVO);
        processo.calcularProximoComparecimento();
        processo.atualizarStatusBaseadoEmData();

        return ProcessoResponseDTO.fromEntity(processoRepository.save(processo));
    }

    // =====================================================================
    // Inner DTO (mantido para retrocompatibilidade — use ContadoresDashboardDTO)
    // =====================================================================

    /**
     * @deprecated Use {@link ContadoresDashboardDTO} diretamente.
     */
    @Deprecated
    @lombok.Data
    @lombok.Builder
    public static class ContadoresDashboard {
        private long totalProcessosAtivos;
        private long emConformidade;
        private long inadimplentes;
        private long comparecimentosHoje;
        private LocalDate dataConsulta;
    }
}
