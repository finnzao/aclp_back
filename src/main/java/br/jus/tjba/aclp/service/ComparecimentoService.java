package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.dto.HistoricoComparecimentoResponseDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    @Transactional
    public HistoricoComparecimentoResponseDTO registrarComparecimento(ComparecimentoDTO dto) {
        limparEFormatarDadosDTO(dto);
        validarComparecimento(dto);

        Processo processo = null;
        Custodiado custodiado;

        if (dto.getProcessoId() != null) {
            processo = processoRepository.findByIdComCustodiado(dto.getProcessoId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Processo não encontrado com ID: " + dto.getProcessoId()));
            custodiado = processo.getCustodiado();
        } else if (dto.getCustodiadoId() != null) {
            custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Custodiado não encontrado com ID: " + dto.getCustodiadoId()));
            List<Processo> processosAtivos = processoRepository.findProcessosAtivosByCustodiado(custodiado.getId());
            if (!processosAtivos.isEmpty()) {
                processo = processosAtivos.get(0);
            }
        } else {
            throw new IllegalArgumentException("processoId ou custodiadoId é obrigatório");
        }

        HistoricoComparecimento historico = HistoricoComparecimento.builder()
                .processo(processo)
                .custodiado(custodiado)
                .dataComparecimento(dto.getDataComparecimento())
                .horaComparecimento(dto.getHoraComparecimento())
                .tipoValidacao(dto.getTipoValidacao())
                .observacoes(dto.getObservacoes())
                .validadoPor(dto.getValidadoPor())
                .anexos(dto.getAnexos())
                .mudancaEndereco(dto.houveMudancaEndereco())
                .motivoMudancaEndereco(dto.getMotivoMudancaEndereco())
                .build();

        HistoricoComparecimento historicoSalvo = historicoComparecimentoRepository.save(historico);

        if (dto.houveMudancaEndereco()) {
            processarMudancaEndereco(dto, custodiado, historicoSalvo);
            historicoSalvo = historicoComparecimentoRepository.save(historicoSalvo);
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

        return toResponseDTO(historicoSalvo);
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarHistoricoPorCustodiado(Long custodiadoId) {
        if (custodiadoId == null || custodiadoId <= 0)
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        return historicoComparecimentoRepository
                .findByCustodiado_IdOrderByDataComparecimentoDesc(custodiadoId)
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarComparecimentosPorPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null)
            throw new IllegalArgumentException("Data de início e fim são obrigatórias");
        if (inicio.isAfter(fim))
            throw new IllegalArgumentException("Data de início não pode ser posterior à data de fim");
        return historicoComparecimentoRepository
                .findByDataComparecimentoBetween(inicio, fim)
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarComparecimentosComMudancaEndereco(Long custodiadoId) {
        if (custodiadoId == null || custodiadoId <= 0)
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        return historicoComparecimentoRepository
                .findByCustodiado_IdAndMudancaEnderecoTrue(custodiadoId)
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimentoResponseDTO> buscarComparecimentosHoje() {
        return historicoComparecimentoRepository
                .findByDataComparecimento(LocalDate.now())
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> buscarTodosComparecimentos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dataComparecimento").descending());
        return historicoComparecimentoRepository
                .findAllByOrderByDataComparecimentoDesc(pageable)
                .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> buscarComparecimentosComFiltros(
            LocalDate dataInicio, LocalDate dataFim, String tipoValidacao, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return historicoComparecimentoRepository
                .findComFiltros(dataInicio, dataFim, tipoValidacao, pageable)
                .map(this::toResponseDTO);
    }

    @Transactional
    public HistoricoComparecimentoResponseDTO atualizarObservacoes(Long historicoId, String observacoes) {
        HistoricoComparecimento historico = historicoComparecimentoRepository.findById(historicoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Histórico não encontrado com ID: " + historicoId));
        historico.setObservacoes(observacoes != null ? observacoes.trim() : null);
        return toResponseDTO(historicoComparecimentoRepository.save(historico));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buscarEstatisticasDetalhadas() {
        Map<String, Object> estatisticas = new HashMap<>();

        long totalCustodiados = custodiadoRepository.countActive();
        long totalComparecimentos = historicoComparecimentoRepository.countTotal();
        long totalMudancasEndereco = historicoComparecimentoRepository.countComMudancaEndereco();

        long custodiadosInadimplentes = custodiadoRepository.countByStatus(StatusComparecimento.INADIMPLENTE);
        long custodiadosEmConformidade = totalCustodiados - custodiadosInadimplentes;

        double percentualConformidade = totalCustodiados > 0
                ? (double) custodiadosEmConformidade / totalCustodiados * 100 : 0.0;
        double percentualInadimplencia = totalCustodiados > 0
                ? (double) custodiadosInadimplentes / totalCustodiados * 100 : 0.0;

        long presenciais = historicoComparecimentoRepository
                .countByTipoValidacao(TipoValidacao.PRESENCIAL.name());
        long online = historicoComparecimentoRepository
                .countByTipoValidacao(TipoValidacao.ONLINE.name());

        estatisticas.put("totalCustodiados", totalCustodiados);
        estatisticas.put("custodiadosEmConformidade", custodiadosEmConformidade);
        estatisticas.put("custodiadosInadimplentes", custodiadosInadimplentes);
        estatisticas.put("percentualConformidade", percentualConformidade);
        estatisticas.put("percentualInadimplencia", percentualInadimplencia);
        estatisticas.put("totalComparecimentos", totalComparecimentos);
        estatisticas.put("comparecimentosPresenciais", presenciais);
        estatisticas.put("comparecimentosOnline", online);
        estatisticas.put("percentualPresencial",
                totalComparecimentos > 0 ? (double) presenciais / totalComparecimentos * 100 : 0.0);
        estatisticas.put("percentualOnline",
                totalComparecimentos > 0 ? (double) online / totalComparecimentos * 100 : 0.0);
        estatisticas.put("totalMudancasEndereco", totalMudancasEndereco);
        estatisticas.put("comparecimentosHoje",
                historicoComparecimentoRepository.countByDataComparecimento(LocalDate.now()));
        estatisticas.put("dataConsulta", LocalDate.now());

        return estatisticas;
    }

    @Transactional(readOnly = true)
    public EstatisticasComparecimento buscarEstatisticas(LocalDate inicio, LocalDate fim) {
        List<HistoricoComparecimento> comparecimentos =
                historicoComparecimentoRepository.findByDataComparecimentoBetween(inicio, fim);
        long total = comparecimentos.size();

        return EstatisticasComparecimento.builder()
                .periodo(inicio + " a " + fim)
                .totalComparecimentos(total)
                .comparecimentosPresenciais(comparecimentos.stream()
                        .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL).count())
                .comparecimentosOnline(comparecimentos.stream()
                        .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE).count())
                .cadastrosIniciais(comparecimentos.stream()
                        .filter(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL).count())
                .mudancasEndereco(comparecimentos.stream()
                        .filter(HistoricoComparecimento::houveMudancaEndereco).count())
                .percentualPresencial(total > 0 ? (double) comparecimentos.stream()
                        .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL).count() / total * 100 : 0.0)
                .percentualOnline(total > 0 ? (double) comparecimentos.stream()
                        .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE).count() / total * 100 : 0.0)
                .build();
    }

    @Transactional(readOnly = true)
    public EstatisticasGerais buscarEstatisticasGerais() {
        long total = historicoComparecimentoRepository.countTotal();
        long presenciais = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.PRESENCIAL.name());
        long online = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.ONLINE.name());
        long cadastros = historicoComparecimentoRepository.countByTipoValidacao(TipoValidacao.CADASTRO_INICIAL.name());
        long mudancas = historicoComparecimentoRepository.countComMudancaEndereco();
        long hoje = historicoComparecimentoRepository.countByDataComparecimento(LocalDate.now());
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        long esteMes = historicoComparecimentoRepository.countByDataComparecimentoBetween(inicioMes, LocalDate.now());
        long custodiadosDistintos = historicoComparecimentoRepository.countCustodiadosDistintos();

        return EstatisticasGerais.builder()
                .totalComparecimentos(total)
                .comparecimentosPresenciais(presenciais)
                .comparecimentosOnline(online)
                .cadastrosIniciais(cadastros)
                .totalMudancasEndereco(mudancas)
                .comparecimentosHoje(hoje)
                .comparecimentosEsteMes(esteMes)
                .custodiadosComComparecimento(custodiadosDistintos)
                .percentualPresencial(total > 0 ? (double) presenciais / total * 100 : 0.0)
                .percentualOnline(total > 0 ? (double) online / total * 100 : 0.0)
                .mediaComparecimentosPorCustodiado(
                        custodiadosDistintos > 0 ? (double) total / custodiadosDistintos : 0.0)
                .build();
    }

    @Transactional(readOnly = true)
    public ResumoSistema buscarResumoSistema() {
        LocalDate hoje = LocalDate.now();

        long totalCustodiados = custodiadoRepository.countActive();
        long custodiadosInadimplentes = custodiadoRepository.countByStatus(StatusComparecimento.INADIMPLENTE);
        long custodiadosEmConformidade = totalCustodiados - custodiadosInadimplentes;

        long comparecimentosHoje = historicoComparecimentoRepository.countByDataComparecimento(hoje);
        long totalComparecimentos = historicoComparecimentoRepository.countTotal();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        long comparecimentosEsteMes = historicoComparecimentoRepository
                .countByDataComparecimentoBetween(inicioMes, hoje);
        long totalMudancasEndereco = historicoComparecimentoRepository.countComMudancaEndereco();
        long enderecosAtivos = historicoEnderecoRepository.findAllEnderecosAtivos().size();
        long custodiadosSemHistorico = custodiadoRepository.findCustodiadosSemHistorico().size();
        long custodiadosSemEnderecoAtivo = historicoEnderecoRepository.countCustodiadosSemEnderecoAtivo();

        return ResumoSistema.builder()
                .totalCustodiados(totalCustodiados)
                .custodiadosEmConformidade(custodiadosEmConformidade)
                .custodiadosInadimplentes(custodiadosInadimplentes)
                .comparecimentosHoje(comparecimentosHoje)
                .totalComparecimentos(totalComparecimentos)
                .comparecimentosEsteMes(comparecimentosEsteMes)
                .totalMudancasEndereco(totalMudancasEndereco)
                .enderecosAtivos(enderecosAtivos)
                .custodiadosSemHistorico(custodiadosSemHistorico)
                .custodiadosSemEnderecoAtivo(custodiadosSemEnderecoAtivo)
                .percentualConformidade(totalCustodiados > 0
                        ? (double) custodiadosEmConformidade / totalCustodiados * 100 : 0.0)
                .percentualInadimplencia(totalCustodiados > 0
                        ? (double) custodiadosInadimplentes / totalCustodiados * 100 : 0.0)
                .dataConsulta(hoje)
                .relatorioUltimosMeses(calcularRelatorioUltimosMeses(6))
                .tendenciaConformidade(calcularTendenciaConformidade(6))
                .proximosComparecimentos(calcularProximosComparecimentos(30))
                .analiseComparecimentos(calcularAnaliseComparecimentos())
                .analiseAtrasos(calcularAnaliseAtrasos())
                .build();
    }

    @Transactional
    public Map<String, Object> migrarCadastrosIniciais(String validadoPor) {
        log.info("Iniciando migração de cadastros iniciais");
        List<Custodiado> todosCustodiados = custodiadoRepository.findAll();
        if (todosCustodiados.isEmpty())
            return Map.of("status", "warning", "message", "Nenhum custodiado encontrado",
                    "totalCustodiados", 0, "custodiadosMigrados", 0);

        int custodiadosMigrados = 0;
        List<String> erros = new ArrayList<>();

        for (Custodiado custodiado : todosCustodiados) {
            try {
                if (!historicoComparecimentoRepository
                        .existsCadastroInicialPorCustodiado(custodiado.getId())) {
                    HistoricoComparecimento cadastroInicial = HistoricoComparecimento.builder()
                            .custodiado(custodiado)
                            .dataComparecimento(custodiado.getDataComparecimentoInicial())
                            .horaComparecimento(LocalTime.now())
                            .tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                            .validadoPor(validadoPor)
                            .observacoes("Cadastro inicial migrado")
                            .mudancaEndereco(Boolean.FALSE)
                            .build();
                    List<Processo> processos =
                            processoRepository.findProcessosAtivosByCustodiado(custodiado.getId());
                    if (!processos.isEmpty())
                        cadastroInicial.setProcesso(processos.get(0));
                    historicoComparecimentoRepository.save(cadastroInicial);
                    custodiadosMigrados++;
                }
            } catch (Exception e) {
                erros.add(String.format("Erro custodiado %s (ID: %d): %s",
                        custodiado.getNome(), custodiado.getId(), e.getMessage()));
                log.error("Erro ao migrar custodiado {}: {}", custodiado.getId(), e.getMessage());
            }
        }

        return Map.of(
                "status", "success",
                "message", String.format("Migração concluída. %d de %d migrados",
                        custodiadosMigrados, todosCustodiados.size()),
                "totalCustodiados", todosCustodiados.size(),
                "custodiadosMigrados", custodiadosMigrados,
                "erros", erros.size(),
                "detalhesErros", erros);
    }

    public HistoricoComparecimentoResponseDTO toResponseDTO(HistoricoComparecimento h) {
        return HistoricoComparecimentoResponseDTO.builder()
                .id(h.getId())
                .custodiadoId(h.getCustodiadoId())
                .custodiadoNome(h.getCustodiadoNome())
                .dataComparecimento(h.getDataComparecimento())
                .horaComparecimento(h.getHoraComparecimento())
                .tipoValidacao(h.getTipoValidacao().name())
                .validadoPor(h.getValidadoPor())
                .observacoes(h.getObservacoes())
                .mudancaEndereco(h.getMudancaEndereco())
                .motivoMudancaEndereco(h.getMotivoMudancaEndereco())
                .build();
    }

    private void limparEFormatarDadosDTO(ComparecimentoDTO dto) {
        if (dto.getValidadoPor() != null) dto.setValidadoPor(dto.getValidadoPor().trim());
        if (dto.getObservacoes() != null) {
            dto.setObservacoes(dto.getObservacoes().trim());
            if (dto.getObservacoes().isEmpty()) dto.setObservacoes(null);
        }
        if (dto.getMotivoMudancaEndereco() != null) {
            dto.setMotivoMudancaEndereco(dto.getMotivoMudancaEndereco().trim());
            if (dto.getMotivoMudancaEndereco().isEmpty()) dto.setMotivoMudancaEndereco(null);
        }
        if (dto.getDataComparecimento() != null && dto.getDataComparecimento().isAfter(LocalDate.now()))
            dto.setDataComparecimento(LocalDate.now());
        if (dto.houveMudancaEndereco() && dto.getNovoEndereco() != null) {
            var end = dto.getNovoEndereco();
            if (end.getCep() != null) {
                String cepLimpo = end.getCep().replaceAll("[^\\d]", "");
                if (cepLimpo.length() == 8)
                    end.setCep(cepLimpo.substring(0, 5) + "-" + cepLimpo.substring(5));
            }
            if (end.getLogradouro() != null) end.setLogradouro(end.getLogradouro().trim());
            if (end.getBairro() != null) end.setBairro(end.getBairro().trim());
            if (end.getCidade() != null) end.setCidade(end.getCidade().trim());
            if (end.getEstado() != null) end.setEstado(end.getEstado().trim().toUpperCase());
        }
    }

    private void validarComparecimento(ComparecimentoDTO dto) {
        if (dto.getProcessoId() == null && dto.getCustodiadoId() == null)
            throw new IllegalArgumentException("processoId ou custodiadoId é obrigatório");
        if (dto.getDataComparecimento() == null)
            throw new IllegalArgumentException("Data do comparecimento é obrigatória");
        if (dto.getDataComparecimento().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Data não pode ser futura");
        if (dto.getTipoValidacao() == null)
            throw new IllegalArgumentException("Tipo de validação é obrigatório");
        if (dto.getValidadoPor() == null || dto.getValidadoPor().trim().isEmpty())
            throw new IllegalArgumentException("Validado por é obrigatório");
        if (dto.houveMudancaEndereco()) {
            if (dto.getNovoEndereco() == null)
                throw new IllegalArgumentException("Dados do novo endereço obrigatórios quando há mudança");
            if (!dto.getNovoEndereco().isCompleto())
                throw new IllegalArgumentException("Preencha todos os campos obrigatórios do endereço");
        }
    }

    private void processarMudancaEndereco(ComparecimentoDTO dto, Custodiado custodiado,
                                           HistoricoComparecimento historico) {
        historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());

        HistoricoEndereco novoHist = HistoricoEndereco.builder()
                .custodiado(custodiado)
                .cep(dto.getNovoEndereco().getCep())
                .logradouro(dto.getNovoEndereco().getLogradouro())
                .numero(dto.getNovoEndereco().getNumero())
                .complemento(dto.getNovoEndereco().getComplemento())
                .bairro(dto.getNovoEndereco().getBairro())
                .cidade(dto.getNovoEndereco().getCidade())
                .estado(dto.getNovoEndereco().getEstado())
                .dataInicio(dto.getDataComparecimento())
                .ativo(Boolean.TRUE)
                .motivoAlteracao(dto.getMotivoMudancaEndereco())
                .validadoPor(dto.getValidadoPor())
                .historicoComparecimento(historico)
                .build();

        HistoricoEndereco salvo = historicoEnderecoRepository.save(novoHist);
        historicoEnderecoRepository.desativarOutrosEnderecosAtivos(custodiado.getId(), salvo.getId());

        if (historico.getEnderecosAlterados() == null)
            historico.setEnderecosAlterados(new ArrayList<>());
        historico.getEnderecosAlterados().add(salvo);
        historico.setMudancaEndereco(Boolean.TRUE);
    }

    @Transactional(readOnly = true)
    protected RelatorioUltimosMeses calcularRelatorioUltimosMeses(int meses) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusMonths(meses);

        long total = historicoComparecimentoRepository.countByDataComparecimentoBetween(inicio, hoje);
        long presenciais = historicoComparecimentoRepository
                .countByTipoValidacaoAndPeriodo(TipoValidacao.PRESENCIAL.name(), inicio, hoje);
        long online = historicoComparecimentoRepository
                .countByTipoValidacaoAndPeriodo(TipoValidacao.ONLINE.name(), inicio, hoje);
        long mudancas = historicoComparecimentoRepository.countMudancasEnderecoBetween(inicio, hoje);

        return RelatorioUltimosMeses.builder()
                .mesesAnalisados(meses)
                .periodoInicio(inicio)
                .periodoFim(hoje)
                .totalComparecimentos(total)
                .comparecimentosPresenciais(presenciais)
                .comparecimentosOnline(online)
                .mudancasEndereco(mudancas)
                .mediaComparecimentosMensal(meses > 0 ? (double) total / meses : 0.0)
                .percentualPresencial(total > 0 ? (double) presenciais / total * 100 : 0.0)
                .percentualOnline(total > 0 ? (double) online / total * 100 : 0.0)
                .build();
    }

    @Transactional(readOnly = true)
    protected List<TendenciaMensal> calcularTendenciaConformidade(int meses) {
        List<TendenciaMensal> tendencias = new ArrayList<>();
        LocalDate hoje = LocalDate.now();
        long totalCustodiados = custodiadoRepository.countActive();

        for (int i = 0; i < meses; i++) {
            LocalDate ref = hoje.minusMonths(i);
            LocalDate inicioMes = ref.withDayOfMonth(1);
            LocalDate fimMes = ref.withDayOfMonth(ref.lengthOfMonth());

            long totalComp = historicoComparecimentoRepository
                    .countByDataComparecimentoBetween(inicioMes, fimMes);
            long custodiadosComp = historicoComparecimentoRepository
                    .countCustodiadosDistintosBetween(inicioMes, fimMes);

            long inadimplentes = totalCustodiados - custodiadosComp;
            double taxaConf = totalCustodiados > 0
                    ? (double) custodiadosComp / totalCustodiados * 100 : 0.0;

            String mesNome = ref.format(DateTimeFormatter.ofPattern("MMMM/yyyy",
                    Locale.forLanguageTag("pt-BR")));

            tendencias.add(TendenciaMensal.builder()
                    .mes(ref.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .mesNome(mesNome)
                    .totalCustodiados(totalCustodiados)
                    .emConformidade(custodiadosComp)
                    .inadimplentes(inadimplentes)
                    .taxaConformidade(taxaConf)
                    .taxaInadimplencia(100.0 - taxaConf)
                    .totalComparecimentos(totalComp)
                    .build());
        }
        Collections.reverse(tendencias);
        return tendencias;
    }

    @Transactional(readOnly = true)
    protected ProximosComparecimentos calcularProximosComparecimentos(int dias) {
        LocalDate hoje = LocalDate.now();
        List<Custodiado> custodiados = custodiadoRepository
                .findByProximoComparecimentoBetween(hoje, hoje.plusDays(dias));

        Map<LocalDate, List<Custodiado>> porDia = new TreeMap<>();
        for (Custodiado c : custodiados) {
            porDia.computeIfAbsent(c.getProximoComparecimento(), k -> new ArrayList<>()).add(c);
        }

        List<ComparecimentoDiario> detalhes = porDia.entrySet().stream()
                .map(e -> ComparecimentoDiario.builder()
                        .data(e.getKey())
                        .diaSemana(e.getKey().format(DateTimeFormatter.ofPattern("EEEE",
                                new Locale("pt", "BR"))))
                        .totalPrevisto(e.getValue().size())
                        .custodiados(e.getValue().stream()
                                .map(c -> DetalheCustodiado.builder()
                                        .id(c.getId())
                                        .nome(c.getNome())
                                        .processo(c.getProcesso())
                                        .periodicidade(c.getPeriodicidadeDescricao())
                                        .diasAtraso(c.getDiasAtraso())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        List<Custodiado> atrasados = custodiadoRepository.findByProximoComparecimentoBefore(hoje);

        return ProximosComparecimentos.builder()
                .diasAnalisados(dias)
                .totalPrevistoProximosDias(custodiados.size())
                .totalAtrasados(atrasados.size())
                .comparecimentosHoje(porDia.getOrDefault(hoje, List.of()).size())
                .comparecimentosAmanha(porDia.getOrDefault(hoje.plusDays(1), List.of()).size())
                .detalhesPorDia(detalhes)
                .custodiadosAtrasados(atrasados.stream().limit(10)
                        .map(c -> DetalheCustodiado.builder()
                                .id(c.getId())
                                .nome(c.getNome())
                                .processo(c.getProcesso())
                                .periodicidade(c.getPeriodicidadeDescricao())
                                .diasAtraso(c.getDiasAtraso())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    protected AnaliseComparecimentos calcularAnaliseComparecimentos() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio30 = hoje.minusDays(30);

        long total = historicoComparecimentoRepository.countByDataComparecimentoBetween(inicio30, hoje);
        long online = historicoComparecimentoRepository
                .countByTipoValidacaoAndPeriodo(TipoValidacao.ONLINE.name(), inicio30, hoje);
        long presenciais = historicoComparecimentoRepository
                .countByTipoValidacaoAndPeriodo(TipoValidacao.PRESENCIAL.name(), inicio30, hoje);

        return AnaliseComparecimentos.builder()
                .comparecimentosUltimos30Dias(total)
                .comparecimentosOnlineUltimos30Dias(online)
                .comparecimentosPresenciaisUltimos30Dias(presenciais)
                .taxaOnlineUltimos30Dias(total > 0 ? (double) online / total * 100 : 0.0)
                .comparecimentosPorDiaSemana(new HashMap<>())
                .comparecimentosPorHora(new HashMap<>())
                .build();
    }

    @Transactional(readOnly = true)
    protected AnaliseAtrasos calcularAnaliseAtrasos() {
        LocalDate hoje = LocalDate.now();
        List<Custodiado> inadimplentes = custodiadoRepository.findInadimplentes();

        Map<String, Long> distribuicao = new LinkedHashMap<>();
        distribuicao.put("1-7 dias", 0L);
        distribuicao.put("8-15 dias", 0L);
        distribuicao.put("16-30 dias", 0L);
        distribuicao.put("31-60 dias", 0L);
        distribuicao.put("61-90 dias", 0L);
        distribuicao.put("Mais de 90 dias", 0L);

        DetalheCustodiadoAtrasado maiorAtraso = null;
        long maiorDias = 0;
        long soma = 0;
        long count = 0;
        long t30 = 0, t60 = 0, t90 = 0, tMais90 = 0;

        for (Custodiado c : inadimplentes) {
            if (c.getProximoComparecimento() != null && c.getProximoComparecimento().isBefore(hoje)) {
                long dias = ChronoUnit.DAYS.between(c.getProximoComparecimento(), hoje);
                soma += dias;
                count++;

                DetalheCustodiadoAtrasado det = DetalheCustodiadoAtrasado.builder()
                        .id(c.getId())
                        .nome(c.getNome())
                        .processo(c.getProcesso())
                        .periodicidade(c.getPeriodicidadeDescricao())
                        .diasAtraso(dias)
                        .dataUltimoComparecimento(c.getUltimoComparecimento())
                        .dataProximoComparecimento(c.getProximoComparecimento())
                        .vara(c.getVara())
                        .comarca(c.getComarca())
                        .contato(c.getContato())
                        .enderecoAtual("Não informado")
                        .build();

                if (dias > maiorDias) {
                    maiorDias = dias;
                    maiorAtraso = det;
                }

                if (dias <= 7) distribuicao.merge("1-7 dias", 1L, Long::sum);
                else if (dias <= 15) distribuicao.merge("8-15 dias", 1L, Long::sum);
                else if (dias <= 30) distribuicao.merge("16-30 dias", 1L, Long::sum);
                else if (dias <= 60) {
                    distribuicao.merge("31-60 dias", 1L, Long::sum);
                    t30++;
                } else if (dias <= 90) {
                    distribuicao.merge("61-90 dias", 1L, Long::sum);
                    t60++;
                } else {
                    distribuicao.merge("Mais de 90 dias", 1L, Long::sum);
                    tMais90++;
                }
                if (dias > 90) t90++;
            }
        }

        return AnaliseAtrasos.builder()
                .totalCustodiadosAtrasados(inadimplentes.size())
                .totalAtrasados30Dias(t30)
                .totalAtrasados60Dias(t60)
                .totalAtrasados90Dias(t90)
                .totalAtrasadosMais90Dias(tMais90)
                .mediaDiasAtraso(count > 0 ? (double) soma / count : 0.0)
                .distribuicaoAtrasos(distribuicao)
                .custodiadoMaiorAtraso(maiorAtraso)
                .dataAnalise(hoje)
                .build();
    }

    @lombok.Data @lombok.Builder
    public static class EstatisticasComparecimento {
        private String periodo;
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long cadastrosIniciais;
        private long mudancasEndereco;
        private double percentualPresencial;
        private double percentualOnline;
    }

    @lombok.Data @lombok.Builder
    public static class EstatisticasGerais {
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long cadastrosIniciais;
        private long totalMudancasEndereco;
        private long comparecimentosHoje;
        private long comparecimentosEsteMes;
        private long custodiadosComComparecimento;
        private double percentualPresencial;
        private double percentualOnline;
        private double mediaComparecimentosPorCustodiado;
    }

    @lombok.Data @lombok.Builder
    public static class ResumoSistema {
        private long totalCustodiados;
        private long custodiadosEmConformidade;
        private long custodiadosInadimplentes;
        private long comparecimentosHoje;
        private long totalComparecimentos;
        private long comparecimentosEsteMes;
        private long totalMudancasEndereco;
        private long enderecosAtivos;
        private long custodiadosSemHistorico;
        private long custodiadosSemEnderecoAtivo;
        private double percentualConformidade;
        private double percentualInadimplencia;
        private LocalDate dataConsulta;
        private RelatorioUltimosMeses relatorioUltimosMeses;
        private List<TendenciaMensal> tendenciaConformidade;
        private ProximosComparecimentos proximosComparecimentos;
        private AnaliseComparecimentos analiseComparecimentos;
        private AnaliseAtrasos analiseAtrasos;
    }

    @lombok.Data @lombok.Builder
    public static class RelatorioUltimosMeses {
        private int mesesAnalisados;
        private LocalDate periodoInicio;
        private LocalDate periodoFim;
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long mudancasEndereco;
        private double mediaComparecimentosMensal;
        private double percentualPresencial;
        private double percentualOnline;
    }

    @lombok.Data @lombok.Builder
    public static class TendenciaMensal {
        private String mes;
        private String mesNome;
        private long totalCustodiados;
        private long emConformidade;
        private long inadimplentes;
        private double taxaConformidade;
        private double taxaInadimplencia;
        private long totalComparecimentos;
    }

    @lombok.Data @lombok.Builder
    public static class ProximosComparecimentos {
        private int diasAnalisados;
        private long totalPrevistoProximosDias;
        private long totalAtrasados;
        private long comparecimentosHoje;
        private long comparecimentosAmanha;
        private List<ComparecimentoDiario> detalhesPorDia;
        private List<DetalheCustodiado> custodiadosAtrasados;
    }

    @lombok.Data @lombok.Builder
    public static class ComparecimentoDiario {
        private LocalDate data;
        private String diaSemana;
        private int totalPrevisto;
        private List<DetalheCustodiado> custodiados;
    }

    @lombok.Data @lombok.Builder
    public static class DetalheCustodiado {
        private Long id;
        private String nome;
        private String processo;
        private String periodicidade;
        private long diasAtraso;
    }

    @lombok.Data @lombok.Builder
    public static class AnaliseComparecimentos {
        private long comparecimentosUltimos30Dias;
        private long comparecimentosOnlineUltimos30Dias;
        private long comparecimentosPresenciaisUltimos30Dias;
        private double taxaOnlineUltimos30Dias;
        private Map<String, Long> comparecimentosPorDiaSemana;
        private Map<Integer, Long> comparecimentosPorHora;
    }

    @lombok.Data @lombok.Builder
    public static class AnaliseAtrasos {
        private long totalCustodiadosAtrasados;
        private long totalAtrasados30Dias;
        private long totalAtrasados60Dias;
        private long totalAtrasados90Dias;
        private long totalAtrasadosMais90Dias;
        private double mediaDiasAtraso;
        private Map<String, Long> distribuicaoAtrasos;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados30Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados60Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados90Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasadosMais90Dias;
        private DetalheCustodiadoAtrasado custodiadoMaiorAtraso;
        private LocalDate dataAnalise;
    }

    @lombok.Data @lombok.Builder
    public static class DetalheCustodiadoAtrasado {
        private Long id;
        private String nome;
        private String processo;
        private String periodicidade;
        private long diasAtraso;
        private LocalDate dataUltimoComparecimento;
        private LocalDate dataProximoComparecimento;
        private String vara;
        private String comarca;
        private String contato;
        private String enderecoAtual;
    }
}
