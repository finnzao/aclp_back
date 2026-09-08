package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CadastroInicialDTO;
import br.jus.tjba.aclp.dto.CadastroInicialResponseDTO;
import br.jus.tjba.aclp.dto.PessoaMonitoradaDTO;
import br.jus.tjba.aclp.dto.PessoaMonitoradaListDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PessoaMonitoradaService {
    // M2 do review: listagem nativa vive em ListagemPessoasService e o fluxo de
    // cadastro/edicao em CadastroPessoaService; aqui ficam buscas e acoes admin.

    private final PessoaMonitoradaRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final ProcessoRepository processoRepository;
    private final ExecucaoPenalService execucaoPenalService;
    private final jakarta.persistence.EntityManager entityManager;

    // =====================================================================
    // CORREÇÃO DE PERFORMANCE: Novos métodos para busca paginada
    // =====================================================================

    /**
     * CORREÇÃO DE PERFORMANCE: Busca paginada de custodiados com filtros.
     *
     * Utiliza Specification do Spring Data JPA para construir queries
     * dinâmicas baseadas nos filtros recebidos, evitando carregar
     * todos os registros em memória.
     *
     * @param page      Número da página (0-indexed)
     * @param size      Quantidade de registros por página
     * @param nome      Filtro opcional por nome (LIKE %nome%)
     * @param cpf       Filtro opcional por CPF
     * @param status    Filtro opcional por status (EM_CONFORMIDADE / INADIMPLENTE)
     * @param ordenarPor Campo para ordenação (nome, proximoComparecimento, status, etc.)
     * @param direcao   Direção da ordenação (asc ou desc)
     */
    // =====================================================================
    // CADASTRO INICIAL — cria tudo em uma única transação (inalterado)
    // =====================================================================

    // =====================================================================
    // Métodos existentes (inalterados)
    // =====================================================================

    @Transactional(readOnly = true)
    public Optional<PessoaMonitorada> findByPublicId(String publicIdStr) {
        if (publicIdStr == null || publicIdStr.trim().isEmpty())
            throw new IllegalArgumentException("publicId não pode ser vazio");
        try {
            UUID publicId = UUID.fromString(publicIdStr);
            // processos junto: os campos derivados da ficha funcionam fora da sessao
            return custodiadoRepository.findByPublicIdComProcessos(publicId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void deleteByPublicId(String publicIdStr, br.jus.tjba.aclp.model.enums.MotivoArquivamento motivo) {
        UUID publicId = parseUuid(publicIdStr);
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Pessoa monitorada já está arquivada");
        custodiado.arquivar(motivo);
        custodiadoRepository.save(custodiado);
    }

    @Transactional
    public PessoaMonitorada declararForagido(String publicIdStr, String motivo, String declaradoPor) {
        if (motivo == null || motivo.trim().isEmpty())
            throw new IllegalArgumentException("Motivo é obrigatório para declarar foragido");
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(parseUuid(publicIdStr))
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        if (custodiado.getSituacao() == SituacaoPessoaMonitorada.FORAGIDO)
            throw new IllegalArgumentException("Pessoa monitorada já está declarada foragida");

        custodiado.declararForagido();
        custodiadoRepository.save(custodiado);
        log.warn("FORAGIDO declarado — custodiado {} por {}: {}", custodiado.getNome(), declaradoPor, motivo);

        // evasao em execucao de pena entra no rito de justificacao (LEP 118 p2)
        for (Processo p : processoRepository.findProcessosAtivosByCustodiado(custodiado.getId())) {
            if (p.isExecucao()) {
                execucaoPenalService.registrarIncidenteEvasao(p.getId(), motivo, declaradoPor);
            }
        }
        return custodiado;
    }

    @Transactional
    public PessoaMonitorada reativarPorPublicId(String publicIdStr) {
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(parseUuid(publicIdStr))
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        if (custodiado.getSituacao() == SituacaoPessoaMonitorada.ATIVO)
            throw new IllegalArgumentException("Pessoa monitorada já está ativa");
        custodiado.reativar();
        return custodiadoRepository.save(custodiado);
    }

    @Transactional(readOnly = true)
    public boolean existeArquivadoComCpf(String cpf) {
        return custodiadoRepository.findByCpfAndSituacao(cpf, SituacaoPessoaMonitorada.ARQUIVADO).isPresent();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAll() { return custodiadoRepository.findAllWithEnderecosAtivos(); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAllIncludingArchived() { return custodiadoRepository.findAllIncludingArchivedWithEnderecos(); }

    @Transactional(readOnly = true)
    public Optional<PessoaMonitorada> findById(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        return custodiadoRepository.findByIdComProcessos(id);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        PessoaMonitorada custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada com ID: " + id));
        if (custodiado.isArquivado()) throw new IllegalArgumentException("Pessoa monitorada já está arquivada");
        custodiado.arquivar();
        custodiadoRepository.save(custodiado);
    }

    @Transactional
    public PessoaMonitorada reativar(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        PessoaMonitorada custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada com ID: " + id));
        if (!custodiado.isArquivado()) throw new IllegalArgumentException("Pessoa monitorada já está ativa");
        custodiado.reativar();
        return custodiadoRepository.save(custodiado);
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findByProcesso(String processo) {
        if (processo == null || processo.trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        return custodiadoRepository.findByProcesso(br.jus.tjba.aclp.util.Formatadores.formatarProcesso(processo.trim()));
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findByProcessoIncludingArchived(String processo) {
        if (processo == null || processo.trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        return custodiadoRepository.findByProcessoIncludingArchived(br.jus.tjba.aclp.util.Formatadores.formatarProcesso(processo.trim()));
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> findByStatus(StatusComparecimento status) {
        if (status == null) throw new IllegalArgumentException("Status é obrigatório");
        var lista = status == StatusComparecimento.INADIMPLENTE
                ? custodiadoRepository.findInadimplentesWithEnderecos()
                : custodiadoRepository.findEmConformidadeAtivos();
        return lista.stream().map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> findComparecimentosHoje() {
        return custodiadoRepository.findComparecimentosHoje().stream()
                .map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> findInadimplentes() {
        return custodiadoRepository.findInadimplentesWithEnderecos().stream()
                .map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> buscarPorNomeOuProcesso(String termo) {
        if (termo == null || termo.trim().isEmpty()) throw new IllegalArgumentException("Termo de busca é obrigatório");
        if (termo.trim().length() < 2) throw new IllegalArgumentException("Termo deve ter pelo menos 2 caracteres");
        String t = termo.trim();
        String tp = t;
        if (t.replaceAll("[^\\d]", "").length() >= 10) { try { tp = br.jus.tjba.aclp.util.Formatadores.formatarProcesso(t); } catch (Exception ignored) {} }
        return custodiadoRepository.buscarPorNomeOuProcessoWithEnderecos(t, tp).stream()
                .map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findBySituacao(SituacaoPessoaMonitorada situacao) { return custodiadoRepository.findBySituacao(situacao); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAllArchived() { return custodiadoRepository.findAllArchived(); }

    @Transactional(readOnly = true)
    public long countBySituacao(SituacaoPessoaMonitorada situacao) { return custodiadoRepository.countBySituacao(situacao); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findReactivatable() { return custodiadoRepository.findReactivatable(); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAllActive() { return custodiadoRepository.findAllActive(); }

    public String getEnderecoCompleto(Long id) {
        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(id).map(HistoricoEndereco::getEnderecoCompleto).orElse("Endereço não informado");
    }

    public String getEnderecoResumido(Long id) {
        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(id).map(HistoricoEndereco::getEnderecoResumido).orElse("Sem endereço");
    }

    public String getCidadeEstado(Long id) {
        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(id).map(e -> e.getCidade() + " - " + e.getEstado()).orElse("Não informado");
    }

    // =====================================================================
    // Métodos privados (inalterados)
    // =====================================================================

    private UUID parseUuid(String v) {
        try { return UUID.fromString(v); } catch (IllegalArgumentException e) { throw new EntityNotFoundException("Identificador inválido: " + v); }
    }
}
