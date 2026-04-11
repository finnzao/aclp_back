package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.dto.HistoricoComparecimentoResponseDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparecimentoService {

    private final CustodiadoRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final ProcessoRepository processoRepository;

    // =====================================================================
    // CORREÇÃO DE PERFORMANCE: Listagem paginada com filtros server-side
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> listarPaginadoComFiltros(
            int page, int size, LocalDate dataInicio, LocalDate dataFim,
            TipoValidacao tipoValidacao, String custodiadoNome, String numeroProcesso) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dataComparecimento"));

        Specification<HistoricoComparecimento> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (dataInicio != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataComparecimento"), dataInicio));
            if (dataFim != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("dataComparecimento"), dataFim));
            if (tipoValidacao != null)
                predicates.add(cb.equal(root.get("tipoValidacao"), tipoValidacao));
            if (custodiadoNome != null && !custodiadoNome.isBlank()) {
                Join<HistoricoComparecimento, Custodiado> cj = root.join("custodiado", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(cj.get("nome")), "%" + custodiadoNome.toLowerCase().trim() + "%"));
            }
            if (numeroProcesso != null && !numeroProcesso.isBlank()) {
                Join<HistoricoComparecimento, Processo> pj = root.join("processo", JoinType.LEFT);
                predicates.add(cb.like(pj.get("numeroProcesso"), "%" + numeroProcesso.trim() + "%"));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        return historicoComparecimentoRepository.findAll(spec, pageable).map(this::toResponseDTO);
    }

    // =====================================================================
    // REGISTRAR COMPARECIMENTO (inalterado)
    // =====================================================================

    @Transactional
    public HistoricoComparecimentoResponseDTO registrarComparecimento(ComparecimentoDTO dto) {
        limparDTO(dto);
        validarComparecimento(dto);

        Processo processo = null;
        Custodiado custodiado;

        if (dto.getProcessoId() != null) {
            processo = processoRepository.findByIdComCustodiado(dto.getProcessoId())
                    .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + dto.getProcessoId()));
            custodiado = processo.getCustodiado();
        } else if (dto.getCustodiadoId() != null) {
            custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                    .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado: " + dto.getCustodiadoId()));
            List<Processo> pa = processoRepository.findProcessosAtivosByCustodiado(custodiado.getId());
            if (!pa.isEmpty()) processo = pa.get(0);
        } else {
            throw new IllegalArgumentException("processoId ou custodiadoId é obrigatório");
        }

        HistoricoComparecimento historico = HistoricoComparecimento.builder()
                .processo(processo).custodiado(custodiado)
                .dataComparecimento(dto.getDataComparecimento()).horaComparecimento(dto.getHoraComparecimento())
                .tipoValidacao(dto.getTipoValidacao()).observacoes(dto.getObservacoes())
                .validadoPor(dto.getValidadoPor()).anexos(dto.getAnexos())
                .mudancaEndereco(dto.houveMudancaEndereco()).motivoMudancaEndereco(dto.getMotivoMudancaEndereco())
                .build();

        HistoricoComparecimento salvo = historicoComparecimentoRepository.save(historico);

        if (dto.houveMudancaEndereco()) {
            processarMudancaEndereco(dto, custodiado, salvo);
            salvo = historicoComparecimentoRepository.save(salvo);
        }

        if (dto.getTipoValidacao() != TipoValidacao.CADASTRO_INICIAL) {
            if (processo != null) {
                processo.setUltimoComparecimento(dto.getDataComparecimento());
                processo.calcularProximoComparecimento();
                processo.atualizarStatusBaseadoEmData();
                processoRepository.save(processo);
            }
            custodiado.setUltimoComparecimento(dto.getDataComparecimento());
            custodiado.calcularProximoComparecimento();
            custodiado.atualizarStatusBaseadoEmData();
            custodiadoRepository.save(custodiado);
        }

        return toResponseDTO(salvo);
    }

    // =====================================================================
    // BUSCAS (inalteradas)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarHistoricoPorCustodiado(Long custodiadoId) {
        if (custodiadoId == null || custodiadoId <= 0) throw new IllegalArgumentException("ID inválido");
        if (!custodiadoRepository.existsById(custodiadoId)) throw new EntityNotFoundException("Custodiado não encontrado: " + custodiadoId);
        return historicoComparecimentoRepository.findByCustodiado_IdOrderByDataComparecimentoDesc(custodiadoId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarComparecimentosPorPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) throw new IllegalArgumentException("Datas obrigatórias");
        if (inicio.isAfter(fim)) throw new IllegalArgumentException("Início não pode ser posterior ao fim");
        return historicoComparecimentoRepository.findByDataComparecimentoBetween(inicio, fim)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarComparecimentosComMudancaEndereco(Long custodiadoId) {
        if (custodiadoId == null || custodiadoId <= 0) throw new IllegalArgumentException("ID inválido");
        return historicoComparecimentoRepository.findByCustodiado_IdAndMudancaEnderecoTrue(custodiadoId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarComparecimentosHoje() {
        return historicoComparecimentoRepository.findByDataComparecimento(LocalDate.now())
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> buscarTodosComparecimentos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dataComparecimento").descending());
        return historicoComparecimentoRepository.findAllByOrderByDataComparecimentoDesc(pageable).map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> buscarComparecimentosComFiltros(
            LocalDate dataInicio, LocalDate dataFim, String tipoValidacao, int page, int size) {
        return historicoComparecimentoRepository.findComFiltros(dataInicio, dataFim, tipoValidacao, PageRequest.of(page, size))
                .map(this::toResponseDTO);
    }

    @Transactional
    public HistoricoComparecimentoResponseDTO atualizarObservacoes(Long historicoId, String observacoes) {
        HistoricoComparecimento h = historicoComparecimentoRepository.findById(historicoId)
                .orElseThrow(() -> new EntityNotFoundException("Histórico não encontrado: " + historicoId));
        h.setObservacoes(observacoes != null ? observacoes.trim() : null);
        return toResponseDTO(historicoComparecimentoRepository.save(h));
    }

    // =====================================================================
    // ESTATÍSTICAS (inalteradas — signatures mantidas)
    // =====================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> buscarEstatisticasDetalhadas() {
        Map<String, Object> est = new HashMap<>();
        long totalCustodiados = custodiadoRepository.countActive();
        long totalComp = historicoComparecimentoRepository.countTotal();
        long inadimplentes = custodiadoRepository.countByStatus(StatusComparecimento.INADIMPLENTE);
        long presenciais = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.PRESENCIAL.name());
        long online = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.ONLINE.name());

        est.put("totalCustodiados", totalCustodiados);
        est.put("custodiadosEmConformidade", totalCustodiados - inadimplentes);
        est.put("custodiadosInadimplentes", inadimplentes);
        est.put("percentualConformidade", totalCustodiados > 0 ? (double)(totalCustodiados - inadimplentes)/totalCustodiados*100 : 0);
        est.put("percentualInadimplencia", totalCustodiados > 0 ? (double)inadimplentes/totalCustodiados*100 : 0);
        est.put("totalComparecimentos", totalComp);
        est.put("comparecimentosPresenciais", presenciais);
        est.put("comparecimentosOnline", online);
        est.put("percentualPresencial", totalComp > 0 ? (double)presenciais/totalComp*100 : 0);
        est.put("percentualOnline", totalComp > 0 ? (double)online/totalComp*100 : 0);
        est.put("totalMudancasEndereco", historicoComparecimentoRepository.countComMudancaEndereco());
        est.put("comparecimentosHoje", historicoComparecimentoRepository.countByDataComparecimento(LocalDate.now()));
        est.put("dataConsulta", LocalDate.now());
        return est;
    }

    @Transactional(readOnly = true)
    public EstatisticasComparecimento buscarEstatisticas(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) throw new IllegalArgumentException("Datas obrigatórias");
        if (inicio.isAfter(fim)) throw new IllegalArgumentException("Início posterior ao fim");
        List<HistoricoComparecimento> comps = historicoComparecimentoRepository.findByDataComparecimentoBetween(inicio, fim);
        long total = comps.size();
        return EstatisticasComparecimento.builder()
                .periodo(inicio + " a " + fim).totalComparecimentos(total)
                .comparecimentosPresenciais(comps.stream().filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL).count())
                .comparecimentosOnline(comps.stream().filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE).count())
                .cadastrosIniciais(comps.stream().filter(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL).count())
                .mudancasEndereco(comps.stream().filter(HistoricoComparecimento::houveMudancaEndereco).count())
                .percentualPresencial(total > 0 ? (double) comps.stream().filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL).count() / total * 100 : 0)
                .percentualOnline(total > 0 ? (double) comps.stream().filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE).count() / total * 100 : 0)
                .build();
    }

    @Transactional(readOnly = true)
    public EstatisticasGerais buscarEstatisticasGerais() {
        long total = historicoComparecimentoRepository.countTotal();
        long pres = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.PRESENCIAL.name());
        long onl = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.ONLINE.name());
        long cad = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.CADASTRO_INICIAL.name());
        long dist = historicoComparecimentoRepository.countCustodiadosDistintos();
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        return EstatisticasGerais.builder()
                .totalComparecimentos(total).comparecimentosPresenciais(pres).comparecimentosOnline(onl)
                .cadastrosIniciais(cad).totalMudancasEndereco(historicoComparecimentoRepository.countComMudancaEndereco())
                .comparecimentosHoje(historicoComparecimentoRepository.countByDataComparecimento(LocalDate.now()))
                .comparecimentosEsteMes(historicoComparecimentoRepository.countByDataComparecimentoBetween(inicioMes, LocalDate.now()))
                .custodiadosComComparecimento(dist)
                .percentualPresencial(total > 0 ? (double)pres/total*100 : 0)
                .percentualOnline(total > 0 ? (double)onl/total*100 : 0)
                .mediaComparecimentosPorCustodiado(dist > 0 ? (double)total/dist : 0)
                .build();
    }

    @Transactional(readOnly = true)
    public ResumoSistema buscarResumoSistema() {
        LocalDate hoje = LocalDate.now();
        long tc = custodiadoRepository.countActive();
        long inad = custodiadoRepository.countByStatus(StatusComparecimento.INADIMPLENTE);
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        return ResumoSistema.builder()
                .totalCustodiados(tc).custodiadosEmConformidade(tc-inad).custodiadosInadimplentes(inad)
                .comparecimentosHoje(historicoComparecimentoRepository.countByDataComparecimento(hoje))
                .totalComparecimentos(historicoComparecimentoRepository.countTotal())
                .comparecimentosEsteMes(historicoComparecimentoRepository.countByDataComparecimentoBetween(inicioMes, hoje))
                .totalMudancasEndereco(historicoComparecimentoRepository.countComMudancaEndereco())
                .enderecosAtivos(historicoEnderecoRepository.findAllEnderecosAtivos().size())
                .custodiadosSemHistorico(custodiadoRepository.findCustodiadosSemHistorico().size())
                .custodiadosSemEnderecoAtivo(historicoEnderecoRepository.countCustodiadosSemEnderecoAtivo())
                .percentualConformidade(tc > 0 ? (double)(tc-inad)/tc*100 : 0)
                .percentualInadimplencia(tc > 0 ? (double)inad/tc*100 : 0)
                .dataConsulta(hoje)
                .build();
    }

    @Transactional
    public Map<String, Object> migrarCadastrosIniciais(String validadoPor) {
        List<Custodiado> todos = custodiadoRepository.findAll();
        if (todos.isEmpty()) return Map.of("status", "warning", "message", "Nenhum custodiado", "totalCustodiados", 0, "custodiadosMigrados", 0);
        int migrados = 0; List<String> erros = new ArrayList<>();
        for (Custodiado c : todos) {
            try {
                if (!historicoComparecimentoRepository.existsCadastroInicialPorCustodiado(c.getId())) {
                    HistoricoComparecimento ci = HistoricoComparecimento.builder()
                            .custodiado(c).dataComparecimento(c.getDataComparecimentoInicial())
                            .horaComparecimento(LocalTime.now()).tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                            .validadoPor(validadoPor).observacoes("Cadastro inicial migrado").mudancaEndereco(Boolean.FALSE).build();
                    List<Processo> procs = processoRepository.findProcessosAtivosByCustodiado(c.getId());
                    if (!procs.isEmpty()) ci.setProcesso(procs.get(0));
                    historicoComparecimentoRepository.save(ci);
                    migrados++;
                }
            } catch (Exception e) { erros.add(c.getNome() + ": " + e.getMessage()); }
        }
        return Map.of("status", "success", "totalCustodiados", todos.size(), "custodiadosMigrados", migrados, "erros", erros.size(), "detalhesErros", erros);
    }

    // =====================================================================
    // toResponseDTO — CORREÇÃO DE PERFORMANCE: inclui numeroProcesso e custodiadoCpf
    // =====================================================================

    public HistoricoComparecimentoResponseDTO toResponseDTO(HistoricoComparecimento h) {
        Long custodiadoId = null; String custodiadoNome = null; String custodiadoCpf = null;

        try {
            if (h.getProcesso() != null && h.getProcesso().getCustodiado() != null) {
                Custodiado c = h.getProcesso().getCustodiado();
                custodiadoId = c.getId(); custodiadoNome = c.getNome(); custodiadoCpf = c.getCpf();
            }
        } catch (Exception e) { /* fallback abaixo */ }

        if (custodiadoId == null) {
            try {
                if (h.getCustodiado() != null) {
                    custodiadoId = h.getCustodiado().getId();
                    custodiadoNome = h.getCustodiado().getNome();
                    custodiadoCpf = h.getCustodiado().getCpf();
                }
            } catch (Exception e) { log.warn("Custodiado inacessível para comparecimento {}", h.getId()); }
        }

        // CORREÇÃO: Extrair numeroProcesso
        String numProc = null;
        try { if (h.getProcesso() != null) numProc = h.getProcesso().getNumeroProcesso(); } catch (Exception ignored) {}

        // Fallback: buscar processo ativo do custodiado
        if (numProc == null && custodiadoId != null) {
            try {
                List<Processo> pa = processoRepository.findByCustodiadoIdAndSituacaoProcesso(custodiadoId, SituacaoProcesso.ATIVO);
                if (!pa.isEmpty()) numProc = pa.get(0).getNumeroProcesso();
            } catch (Exception ignored) {}
        }

        String tipoStr = null;
        try { if (h.getTipoValidacao() != null) tipoStr = h.getTipoValidacao().name(); } catch (Exception ignored) {}

        return HistoricoComparecimentoResponseDTO.builder()
                .id(h.getId()).custodiadoId(custodiadoId).custodiadoNome(custodiadoNome)
                .dataComparecimento(h.getDataComparecimento()).horaComparecimento(h.getHoraComparecimento())
                .tipoValidacao(tipoStr).validadoPor(h.getValidadoPor()).observacoes(h.getObservacoes())
                .mudancaEndereco(h.getMudancaEndereco()).motivoMudancaEndereco(h.getMotivoMudancaEndereco())
                .numeroProcesso(numProc).custodiadoCpf(custodiadoCpf)
                .build();
    }

    // =====================================================================
    // Métodos privados (inalterados)
    // =====================================================================

    private void limparDTO(ComparecimentoDTO dto) {
        if (dto.getValidadoPor() != null) dto.setValidadoPor(dto.getValidadoPor().trim());
        if (dto.getObservacoes() != null) { dto.setObservacoes(dto.getObservacoes().trim()); if (dto.getObservacoes().isEmpty()) dto.setObservacoes(null); }
        if (dto.getMotivoMudancaEndereco() != null) { dto.setMotivoMudancaEndereco(dto.getMotivoMudancaEndereco().trim()); if (dto.getMotivoMudancaEndereco().isEmpty()) dto.setMotivoMudancaEndereco(null); }
        if (dto.getDataComparecimento() != null && dto.getDataComparecimento().isAfter(LocalDate.now())) dto.setDataComparecimento(LocalDate.now());
    }

    private void validarComparecimento(ComparecimentoDTO dto) {
        if (dto.getProcessoId() == null && dto.getCustodiadoId() == null) throw new IllegalArgumentException("processoId ou custodiadoId obrigatório");
        if (dto.getDataComparecimento() == null) throw new IllegalArgumentException("Data obrigatória");
        if (dto.getDataComparecimento().isAfter(LocalDate.now())) throw new IllegalArgumentException("Data não pode ser futura");
        if (dto.getTipoValidacao() == null) throw new IllegalArgumentException("Tipo de validação obrigatório");
        if (dto.getValidadoPor() == null || dto.getValidadoPor().trim().isEmpty()) throw new IllegalArgumentException("Validado por obrigatório");
        if (dto.houveMudancaEndereco()) {
            if (dto.getNovoEndereco() == null) throw new IllegalArgumentException("Dados do novo endereço obrigatórios");
            if (!dto.getNovoEndereco().isCompleto()) throw new IllegalArgumentException("Preencha todos os campos do endereço");
        }
    }

    private void processarMudancaEndereco(ComparecimentoDTO dto, Custodiado custodiado, HistoricoComparecimento historico) {
        historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());
        HistoricoEndereco novo = HistoricoEndereco.builder()
                .custodiado(custodiado).cep(dto.getNovoEndereco().getCep())
                .logradouro(dto.getNovoEndereco().getLogradouro()).numero(dto.getNovoEndereco().getNumero())
                .complemento(dto.getNovoEndereco().getComplemento()).bairro(dto.getNovoEndereco().getBairro())
                .cidade(dto.getNovoEndereco().getCidade()).estado(dto.getNovoEndereco().getEstado())
                .dataInicio(dto.getDataComparecimento()).ativo(Boolean.TRUE)
                .motivoAlteracao(dto.getMotivoMudancaEndereco()).validadoPor(dto.getValidadoPor())
                .historicoComparecimento(historico).build();
        HistoricoEndereco salvo = historicoEnderecoRepository.save(novo);
        historicoEnderecoRepository.desativarOutrosEnderecosAtivos(custodiado.getId(), salvo.getId());
        if (historico.getEnderecosAlterados() == null) historico.setEnderecosAlterados(new ArrayList<>());
        historico.getEnderecosAlterados().add(salvo);
        historico.setMudancaEndereco(Boolean.TRUE);
    }

    // Inner DTOs (inalterados)
    @lombok.Data @lombok.Builder
    public static class EstatisticasComparecimento {
        private String periodo; private long totalComparecimentos; private long comparecimentosPresenciais;
        private long comparecimentosOnline; private long cadastrosIniciais; private long mudancasEndereco;
        private double percentualPresencial; private double percentualOnline;
    }

    @lombok.Data @lombok.Builder
    public static class EstatisticasGerais {
        private long totalComparecimentos; private long comparecimentosPresenciais; private long comparecimentosOnline;
        private long cadastrosIniciais; private long totalMudancasEndereco; private long comparecimentosHoje;
        private long comparecimentosEsteMes; private long custodiadosComComparecimento;
        private double percentualPresencial; private double percentualOnline; private double mediaComparecimentosPorCustodiado;
    }

    @lombok.Data @lombok.Builder
    public static class ResumoSistema {
        private long totalCustodiados; private long custodiadosEmConformidade; private long custodiadosInadimplentes;
        private long comparecimentosHoje; private long totalComparecimentos; private long comparecimentosEsteMes;
        private long totalMudancasEndereco; private long enderecosAtivos; private long custodiadosSemHistorico;
        private long custodiadosSemEnderecoAtivo; private double percentualConformidade; private double percentualInadimplencia;
        private LocalDate dataConsulta;
    }
}
