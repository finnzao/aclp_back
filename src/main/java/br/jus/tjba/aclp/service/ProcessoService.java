package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ProcessoDTO;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessoService {

    private final ProcessoRepository processoRepository;
    private final CustodiadoRepository custodiadoRepository;

    @Transactional(readOnly = true)
    public Page<Processo> listarComFiltros(String termo, StatusComparecimento status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("proximoComparecimento").ascending());
        return processoRepository.findComFiltros(
                (termo != null && termo.isBlank()) ? null : termo,
                status,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Processo buscarPorId(Long id) {
        return processoRepository.findByIdComCustodiado(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado com ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Processo> buscarPorCustodiado(Long custodiadoId) {
        return processoRepository.findProcessosAtivosByCustodiado(custodiadoId);
    }

    @Transactional(readOnly = true)
    public Optional<Processo> buscarPorNumeroProcesso(String numero) {
        return processoRepository.findByNumeroProcesso(numero);
    }

    @Transactional
    public Processo criarProcesso(ProcessoDTO dto) {
        log.info("Criando processo {} para custodiado ID: {}", dto.getNumeroProcesso(), dto.getCustodiadoId());

        Custodiado custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + dto.getCustodiadoId()));

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
        log.info("Processo criado - ID: {}, Número: {}", salvo.getId(), salvo.getNumeroProcesso());
        return salvo;
    }

    @Transactional
    public Processo atualizarProcesso(Long id, ProcessoDTO dto) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado com ID: " + id));

        processo.setNumeroProcesso(dto.getNumeroProcesso());
        processo.setVara(dto.getVara().trim());
        processo.setComarca(dto.getComarca().trim());
        processo.setDataDecisao(dto.getDataDecisao());
        processo.setPeriodicidade(dto.getPeriodicidade());
        processo.setDataComparecimentoInicial(dto.getDataComparecimentoInicial());
        processo.setObservacoes(dto.getObservacoes());
        processo.calcularProximoComparecimento();

        return processoRepository.save(processo);
    }

    @Transactional
    public Processo encerrarProcesso(Long id) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado com ID: " + id));

        processo.setSituacaoProcesso(SituacaoProcesso.ENCERRADO);
        processo.setProximoComparecimento(null);
        log.info("Processo encerrado - ID: {}, Número: {}", id, processo.getNumeroProcesso());
        return processoRepository.save(processo);
    }

    @Transactional
    public Processo suspenderProcesso(Long id) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado com ID: " + id));

        processo.setSituacaoProcesso(SituacaoProcesso.SUSPENSO);
        processo.setProximoComparecimento(null);
        return processoRepository.save(processo);
    }

    @Transactional
    public Processo reativarProcesso(Long id) {
        Processo processo = processoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado com ID: " + id));

        processo.setSituacaoProcesso(SituacaoProcesso.ATIVO);
        processo.calcularProximoComparecimento();
        processo.atualizarStatusBaseadoEmData();
        return processoRepository.save(processo);
    }

    @Transactional(readOnly = true)
    public List<Processo> buscarInadimplentes() {
        return processoRepository.findInadimplentesComCustodiado();
    }

    @Transactional(readOnly = true)
    public List<Processo> buscarComparecimentosHoje() {
        return processoRepository.findComparecimentosHojeComCustodiado();
    }

    @Transactional(readOnly = true)
    public ContadoresDashboard contadoresParaDashboard() {
        long total = processoRepository.countBySituacaoProcesso(SituacaoProcesso.ATIVO);
        long conformidade = processoRepository.countByStatusAtivo(StatusComparecimento.EM_CONFORMIDADE);
        long inadimplentes = processoRepository.countByStatusAtivo(StatusComparecimento.INADIMPLENTE);
        long hoje = processoRepository.findComparecimentosHojeComCustodiado().size();

        return ContadoresDashboard.builder()
                .totalProcessosAtivos(total)
                .emConformidade(conformidade)
                .inadimplentes(inadimplentes)
                .comparecimentosHoje(hoje)
                .dataConsulta(LocalDate.now())
                .build();
    }

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
