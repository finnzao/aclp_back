package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.dto.HistoricoComparecimentoResponseDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
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
import java.time.YearMonth;
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

    @Transactional
    public HistoricoComparecimento registrarComparecimento(ComparecimentoDTO dto) {
        log.info("Iniciando registro de comparecimento - Custodiado ID: {}, Tipo: {}",
                dto.getCustodiadoId(), dto.getTipoValidacao());

        limparEFormatarDadosDTO(dto);
        validarComparecimento(dto);

        Custodiado custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + dto.getCustodiadoId()));

        HistoricoComparecimento historico = criarHistoricoComparecimento(dto, custodiado);
        HistoricoComparecimento historicoSalvo = historicoComparecimentoRepository.save(historico);

        if (dto.houveMudancaEndereco()) {
            processarMudancaEndereco(dto, custodiado, historicoSalvo);
            historicoSalvo = historicoComparecimentoRepository.save(historicoSalvo);
        }

        atualizarDadosCustodiado(custodiado, dto);

        log.info("Comparecimento registrado com sucesso - ID: {}, Custodiado: {}, Mudança endereço: {}",
                historicoSalvo.getId(), custodiado.getNome(), dto.houveMudancaEndereco());

        return historicoSalvo;
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarHistoricoPorCustodiado(Long custodiadoId) {
        log.info("Buscando histórico de comparecimentos - Custodiado ID: {}", custodiadoId);

        if (custodiadoId == null || custodiadoId <= 0) {
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        }

        return historicoComparecimentoRepository.findByCustodiado_IdOrderByDataComparecimentoDesc(custodiadoId);
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarComparecimentosPorPeriodo(LocalDate inicio, LocalDate fim) {
        log.info("Buscando comparecimentos por período: {} a {}", inicio, fim);

        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Data de início e fim são obrigatórias");
        }

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Data de início não pode ser posterior à data de fim");
        }

        return historicoComparecimentoRepository.findByDataComparecimentoBetween(inicio, fim);
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarComparecimentosComMudancaEndereco(Long custodiadoId) {
        log.info("Buscando comparecimentos com mudança de endereço - Custodiado ID: {}", custodiadoId);

        if (custodiadoId == null || custodiadoId <= 0) {
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        }

        return historicoComparecimentoRepository.findByCustodiado_IdAndMudancaEnderecoTrue(custodiadoId);
    }

    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarComparecimentosHoje() {
        log.info("Buscando comparecimentos de hoje");
        return historicoComparecimentoRepository.findByDataComparecimento(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> buscarTodosComparecimentos(int page, int size) {
        log.info("Buscando todos os comparecimentos - Página: {}, Size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("dataComparecimento").descending());
        Page<HistoricoComparecimento> comparecimentos =
                historicoComparecimentoRepository.findAllByOrderByDataComparecimentoDesc(pageable);
        return comparecimentos.map(this::toResponseDTO);
    }

    // Método auxiliar de conversão
    private HistoricoComparecimentoResponseDTO toResponseDTO(HistoricoComparecimento historico) {
        return HistoricoComparecimentoResponseDTO.builder()
                .id(historico.getId())
                .custodiadoId(historico.getCustodiado().getId())
                .custodiadoNome(historico.getCustodiado().getNome())
                .dataComparecimento(historico.getDataComparecimento())
                .horaComparecimento(historico.getHoraComparecimento())
                .tipoValidacao(historico.getTipoValidacao().name())
                .validadoPor(historico.getValidadoPor())
                .observacoes(historico.getObservacoes())
                .mudancaEndereco(historico.getMudancaEndereco())
                .motivoMudancaEndereco(historico.getMotivoMudancaEndereco())
                .build();
    }

    // Buscar comparecimentos com filtros
    // Buscar comparecimentos com filtros
    @Transactional(readOnly = true)
    public Page<HistoricoComparecimentoResponseDTO> buscarComparecimentosComFiltros(LocalDate dataInicio,
                                                                                    LocalDate dataFim,
                                                                                    String tipoValidacao,
                                                                                    int page,
                                                                                    int size) {

        log.info("Buscando comparecimentos com filtros - Início: {}, Fim: {}, Tipo: {}",
                dataInicio, dataFim, tipoValidacao);

        Pageable pageable = PageRequest.of(page, size);
        Page<HistoricoComparecimento> comparecimentos =
                historicoComparecimentoRepository.findComFiltros(dataInicio, dataFim, tipoValidacao, pageable);

        return comparecimentos.map(this::toResponseDTO);  // ✅ ADICIONA CONVERSÃO PARA DTO
    }





    /**
     * Busca estatísticas detalhadas
     * OTIMIZADO: Usa queries com JOIN FETCH e processamento em memória
     */
    public Map<String, Object> buscarEstatisticasDetalhadas() {
        Map<String, Object> estatisticas = new HashMap<>();

        // Contadores básicos (queries simples, sem JOIN)
        long totalCustodiados = custodiadoRepository.countActive();
        long totalComparecimentos = historicoComparecimentoRepository.count();
        long totalMudancasEndereco = historicoComparecimentoRepository.countComMudancaEndereco();

        // Buscar inadimplentes COM endereços (única query com JOIN FETCH)
        List<Custodiado> inadimplentes = custodiadoRepository.findInadimplentesWithEnderecos();

        long custodiadosInadimplentes = inadimplentes.size();
        long custodiadosEmConformidade = totalCustodiados - custodiadosInadimplentes;

        double percentualConformidade = totalCustodiados > 0 ?
                (double) custodiadosEmConformidade / totalCustodiados * 100 : 0.0;
        double percentualInadimplencia = totalCustodiados > 0 ?
                (double) custodiadosInadimplentes / totalCustodiados * 100 : 0.0;

        // Comparecimentos por tipo (query única)
        long presenciais = historicoComparecimentoRepository
                .countByTipoValidacao(TipoValidacao.PRESENCIAL.name());
        long online = historicoComparecimentoRepository
                .countByTipoValidacao(TipoValidacao.ONLINE.name());

        double percentualPresencial = totalComparecimentos > 0 ?
                (double) presenciais / totalComparecimentos * 100 : 0.0;
        double percentualOnline = totalComparecimentos > 0 ?
                (double) online / totalComparecimentos * 100 : 0.0;

        // Montar resposta
        estatisticas.put("totalCustodiados", totalCustodiados);
        estatisticas.put("custodiadosEmConformidade", custodiadosEmConformidade);
        estatisticas.put("custodiadosInadimplentes", custodiadosInadimplentes);
        estatisticas.put("percentualConformidade", percentualConformidade);
        estatisticas.put("percentualInadimplencia", percentualInadimplencia);

        estatisticas.put("totalComparecimentos", totalComparecimentos);
        estatisticas.put("comparecimentosPresenciais", presenciais);
        estatisticas.put("comparecimentosOnline", online);
        estatisticas.put("percentualPresencial", percentualPresencial);
        estatisticas.put("percentualOnline", percentualOnline);

        estatisticas.put("totalMudancasEndereco", totalMudancasEndereco);

        estatisticas.put("comparecimentosHoje",
                custodiadoRepository.findComparecimentosHoje().size());

        estatisticas.put("dataConsulta", LocalDate.now());

        return estatisticas;
    }


    @Transactional
    public HistoricoComparecimento atualizarObservacoes(Long historicoId, String observacoes) {
        log.info("Atualizando observações do comparecimento ID: {}", historicoId);

        HistoricoComparecimento historico = historicoComparecimentoRepository.findById(historicoId)
                .orElseThrow(() -> new EntityNotFoundException("Histórico de comparecimento não encontrado com ID: " + historicoId));

        historico.setObservacoes(observacoes != null ? observacoes.trim() : null);

        return historicoComparecimentoRepository.save(historico);
    }

    @Transactional(readOnly = true)
    public EstatisticasComparecimento buscarEstatisticas(LocalDate inicio, LocalDate fim) {
        log.info("Buscando estatísticas de comparecimentos: {} a {}", inicio, fim);

        List<HistoricoComparecimento> comparecimentos =
                historicoComparecimentoRepository.findByDataComparecimentoBetween(inicio, fim);

        long totalComparecimentos = comparecimentos.size();
        long comparecimentosPresenciais = comparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();
        long comparecimentosOnline = comparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();
        long cadastrosIniciais = comparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL)
                .count();
        long mudancasEndereco = comparecimentos.stream()
                .filter(HistoricoComparecimento::houveMudancaEndereco)
                .count();

        return EstatisticasComparecimento.builder()
                .periodo(inicio + " a " + fim)
                .totalComparecimentos(totalComparecimentos)
                .comparecimentosPresenciais(comparecimentosPresenciais)
                .comparecimentosOnline(comparecimentosOnline)
                .cadastrosIniciais(cadastrosIniciais)
                .mudancasEndereco(mudancasEndereco)
                .percentualPresencial(totalComparecimentos > 0 ?
                        (double) comparecimentosPresenciais / totalComparecimentos * 100 : 0.0)
                .percentualOnline(totalComparecimentos > 0 ?
                        (double) comparecimentosOnline / totalComparecimentos * 100 : 0.0)
                .build();
    }

    @Transactional(readOnly = true)
    public EstatisticasGerais buscarEstatisticasGerais() {
        log.info("Buscando estatísticas gerais de comparecimentos");

        long totalComparecimentos = historicoComparecimentoRepository.count();
        List<HistoricoComparecimento> todosComparecimentos = historicoComparecimentoRepository.findAll();

        long comparecimentosPresenciais = todosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();
        long comparecimentosOnline = todosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();
        long cadastrosIniciais = todosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL)
                .count();

        long totalMudancasEndereco = todosComparecimentos.stream()
                .filter(HistoricoComparecimento::houveMudancaEndereco)
                .count();

        long comparecimentosHoje = historicoComparecimentoRepository
                .findByDataComparecimento(LocalDate.now()).size();

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now();
        long comparecimentosEsteMes = historicoComparecimentoRepository
                .findByDataComparecimentoBetween(inicioMes, fimMes).size();

        long custodiadosComComparecimento = todosComparecimentos.stream()
                .map(h -> h.getCustodiado().getId())
                .distinct()
                .count();

        return EstatisticasGerais.builder()
                .totalComparecimentos(totalComparecimentos)
                .comparecimentosPresenciais(comparecimentosPresenciais)
                .comparecimentosOnline(comparecimentosOnline)
                .cadastrosIniciais(cadastrosIniciais)
                .totalMudancasEndereco(totalMudancasEndereco)
                .comparecimentosHoje(comparecimentosHoje)
                .comparecimentosEsteMes(comparecimentosEsteMes)
                .custodiadosComComparecimento(custodiadosComComparecimento)
                .percentualPresencial(totalComparecimentos > 0 ?
                        (double) comparecimentosPresenciais / totalComparecimentos * 100 : 0.0)
                .percentualOnline(totalComparecimentos > 0 ?
                        (double) comparecimentosOnline / totalComparecimentos * 100 : 0.0)
                .mediaComparecimentosPorCustodiado(custodiadosComComparecimento > 0 ?
                        (double) totalComparecimentos / custodiadosComComparecimento : 0.0)
                .build();
    }

    /**
     * Busca resumo completo do sistema
     */
    public ResumoSistema buscarResumoSistema() {
        LocalDate hoje = LocalDate.now();

        // Contadores básicos
        long totalCustodiados = custodiadoRepository.countActive();
        long custodiadosSemHistorico = custodiadoRepository.findCustodiadosSemHistorico().size();
        long custodiadosSemEnderecoAtivo = historicoEnderecoRepository.countCustodiadosSemEnderecoAtivo();
        long enderecosAtivos = historicoEnderecoRepository.findAllEnderecosAtivos().size();

        // Buscar inadimplentes COM endereços (query única)
        List<Custodiado> inadimplentes = custodiadoRepository.findInadimplentesWithEnderecos();
        long custodiadosInadimplentes = inadimplentes.size();
        long custodiadosEmConformidade = totalCustodiados - custodiadosInadimplentes;

        double percentualConformidade = totalCustodiados > 0 ?
                (double) custodiadosEmConformidade / totalCustodiados * 100 : 0.0;
        double percentualInadimplencia = totalCustodiados > 0 ?
                (double) custodiadosInadimplentes / totalCustodiados * 100 : 0.0;

        // Comparecimentos
        long comparecimentosHoje = custodiadoRepository.findComparecimentosHoje().size();
        long totalComparecimentos = historicoComparecimentoRepository.count();

        LocalDate inicioMes = hoje.withDayOfMonth(1);
        long comparecimentosEsteMes = historicoComparecimentoRepository
                .findByDataComparecimentoBetween(inicioMes, hoje).size();

        long totalMudancasEndereco = historicoComparecimentoRepository.countComMudancaEndereco();

        // Análises detalhadas
        RelatorioUltimosMeses relatorioUltimosMeses = calcularRelatorioUltimosMeses(6);
        List<TendenciaMensal> tendenciaConformidade = calcularTendenciaConformidade(6);
        ProximosComparecimentos proximosComparecimentos = calcularProximosComparecimentos(30);
        AnaliseComparecimentos analiseComparecimentos = calcularAnaliseComparecimentos();
        AnaliseAtrasos analiseAtrasos = calcularAnaliseAtrasos();

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
                .percentualConformidade(percentualConformidade)
                .percentualInadimplencia(percentualInadimplencia)
                .dataConsulta(hoje)
                .relatorioUltimosMeses(relatorioUltimosMeses)
                .tendenciaConformidade(tendenciaConformidade)
                .proximosComparecimentos(proximosComparecimentos)
                .analiseComparecimentos(analiseComparecimentos)
                .analiseAtrasos(analiseAtrasos)
                .build();
    }

    /**
     * Calcula relatório dos últimos meses
     */
    private RelatorioUltimosMeses calcularRelatorioUltimosMeses(int meses) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataInicio = hoje.minusMonths(meses);

        // Buscar todos os comparecimentos do período em uma única query
        List<HistoricoComparecimento> comparecimentos =
                historicoComparecimentoRepository.findByDataComparecimentoBetween(dataInicio, hoje);

        long totalComparecimentos = comparecimentos.size();

        long presenciais = comparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();

        long online = comparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();

        long mudancasEndereco = comparecimentos.stream()
                .filter(HistoricoComparecimento::houveMudancaEndereco)
                .count();

        double mediaComparecimentosMensal = (double) totalComparecimentos / meses;
        double percentualPresencial = totalComparecimentos > 0 ?
                (double) presenciais / totalComparecimentos * 100 : 0.0;
        double percentualOnline = totalComparecimentos > 0 ?
                (double) online / totalComparecimentos * 100 : 0.0;

        return RelatorioUltimosMeses.builder()
                .mesesAnalisados(meses)
                .periodoInicio(dataInicio)
                .periodoFim(hoje)
                .totalComparecimentos(totalComparecimentos)
                .comparecimentosPresenciais(presenciais)
                .comparecimentosOnline(online)
                .mudancasEndereco(mudancasEndereco)
                .mediaComparecimentosMensal(mediaComparecimentosMensal)
                .percentualPresencial(percentualPresencial)
                .percentualOnline(percentualOnline)
                .build();
    }


    /**
     * Calcula tendência mensal de conformidade
     * OTIMIZADO: Agrupa dados em memória após busca única
     */
    private List<TendenciaMensal> calcularTendenciaConformidade(int meses) {
        List<TendenciaMensal> tendencias = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (int i = 0; i < meses; i++) {
            LocalDate mesReferencia = hoje.minusMonths(i);
            LocalDate inicioMes = mesReferencia.withDayOfMonth(1);
            LocalDate fimMes = mesReferencia.withDayOfMonth(mesReferencia.lengthOfMonth());

            // Buscar comparecimentos do mês
            List<HistoricoComparecimento> comparecimentosMes =
                    historicoComparecimentoRepository.findByDataComparecimentoBetween(inicioMes, fimMes);

            // Contar custodiados únicos que compareceram
            long custodiadosComComparecimento = comparecimentosMes.stream()
                    .map(h -> h.getCustodiado().getId())
                    .distinct()
                    .count();

            // Total de custodiados ativos (aproximação)
            long totalCustodiados = custodiadoRepository.countActive();

            long emConformidade = custodiadosComComparecimento;
            long inadimplentes = totalCustodiados - custodiadosComComparecimento;

            double taxaConformidade = totalCustodiados > 0 ?
                    (double) emConformidade / totalCustodiados * 100 : 0.0;
            double taxaInadimplencia = 100.0 - taxaConformidade;

            String mesNome = mesReferencia.format(
                    java.time.format.DateTimeFormatter.ofPattern("MMMM/yyyy",
                            java.util.Locale.forLanguageTag("pt-BR")));

            tendencias.add(TendenciaMensal.builder()
                    .mes(mesReferencia.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")))
                    .mesNome(mesNome)
                    .totalCustodiados(totalCustodiados)
                    .emConformidade(emConformidade)
                    .inadimplentes(inadimplentes)
                    .taxaConformidade(taxaConformidade)
                    .taxaInadimplencia(taxaInadimplencia)
                    .totalComparecimentos((long) comparecimentosMes.size())
                    .build());
        }

        Collections.reverse(tendencias);
        return tendencias;
    }


    private ProximosComparecimentos calcularProximosComparecimentos(int dias) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = hoje.plusDays(dias);

        List<Custodiado> custodiadosComComparecimento =
                custodiadoRepository.findByProximoComparecimentoBetween(hoje, dataLimite);

        Map<LocalDate, List<Custodiado>> comparecimentosPorDia = new TreeMap<>();
        for (Custodiado custodiado : custodiadosComComparecimento) {
            LocalDate dataComparecimento = custodiado.getProximoComparecimento();
            comparecimentosPorDia.computeIfAbsent(dataComparecimento, k -> new ArrayList<>()).add(custodiado);
        }

        List<ComparecimentoDiario> detalhesPorDia = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Custodiado>> entry : comparecimentosPorDia.entrySet()) {
            LocalDate data = entry.getKey();
            List<Custodiado> custodiados = entry.getValue();

            detalhesPorDia.add(ComparecimentoDiario.builder()
                    .data(data)
                    .diaSemana(data.format(DateTimeFormatter.ofPattern("EEEE", new Locale("pt", "BR"))))
                    .totalPrevisto(custodiados.size())
                    .custodiados(custodiados.stream()
                            .map(c -> DetalheCustodiado.builder()
                                    .id(c.getId())
                                    .nome(c.getNome())
                                    .processo(c.getProcesso())
                                    .periodicidade(c.getPeriodicidadeDescricao())
                                    .diasAtraso(c.getDiasAtraso())
                                    .build())
                            .collect(Collectors.toList()))
                    .build());
        }

        List<Custodiado> custodiadosAtrasados = custodiadoRepository.findByProximoComparecimentoBefore(hoje);

        return ProximosComparecimentos.builder()
                .diasAnalisados(dias)
                .totalPrevistoProximosDias(custodiadosComComparecimento.size())
                .totalAtrasados(custodiadosAtrasados.size())
                .comparecimentosHoje(comparecimentosPorDia.getOrDefault(hoje, new ArrayList<>()).size())
                .comparecimentosAmanha(comparecimentosPorDia.getOrDefault(hoje.plusDays(1), new ArrayList<>()).size())
                .detalhesPorDia(detalhesPorDia)
                .custodiadosAtrasados(custodiadosAtrasados.stream()
                        .limit(10)
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

    /**
     * Calcula análise de comparecimentos
     */
    private AnaliseComparecimentos calcularAnaliseComparecimentos() {
        LocalDate hoje = LocalDate.now();
        LocalDate dataInicio = hoje.minusDays(30);

        // Buscar comparecimentos dos últimos 30 dias
        List<HistoricoComparecimento> ultimosComparecimentos =
                historicoComparecimentoRepository.findByDataComparecimentoBetween(dataInicio, hoje);

        long totalComparecimentos = ultimosComparecimentos.size();

        long comparecimentosOnline = ultimosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();

        long comparecimentosPresenciais = ultimosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();

        double taxaOnline = totalComparecimentos > 0 ?
                (double) comparecimentosOnline / totalComparecimentos * 100 : 0.0;

        // Agrupar por dia da semana
        Map<String, Long> porDiaSemana = ultimosComparecimentos.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getDataComparecimento().getDayOfWeek().getDisplayName(
                                java.time.format.TextStyle.FULL,
                                java.util.Locale.forLanguageTag("pt-BR")),
                        Collectors.counting()
                ));

        // Agrupar por hora (se houver hora registrada)
        Map<Integer, Long> porHora = ultimosComparecimentos.stream()
                .filter(h -> h.getHoraComparecimento() != null)
                .collect(Collectors.groupingBy(
                        h -> h.getHoraComparecimento().getHour(),
                        Collectors.counting()
                ));

        return AnaliseComparecimentos.builder()
                .comparecimentosUltimos30Dias(totalComparecimentos)
                .comparecimentosOnlineUltimos30Dias(comparecimentosOnline)
                .comparecimentosPresenciaisUltimos30Dias(comparecimentosPresenciais)
                .taxaOnlineUltimos30Dias(taxaOnline)
                .comparecimentosPorDiaSemana(porDiaSemana)
                .comparecimentosPorHora(porHora)
                .build();
    }


    /**
     * Calcula análise detalhada de atrasos
     * OTIMIZADO: Usa query com JOIN FETCH para evitar N+1 queries
     */
    private AnaliseAtrasos calcularAnaliseAtrasos() {
        LocalDate hoje = LocalDate.now();

        // Buscar inadimplentes COM endereços em uma única query
        List<Custodiado> custodiadosInadimplentes = custodiadoRepository.findInadimplentesWithEnderecos();

        List<DetalheCustodiadoAtrasado> atrasados30Dias = new ArrayList<>();
        List<DetalheCustodiadoAtrasado> atrasados60Dias = new ArrayList<>();
        List<DetalheCustodiadoAtrasado> atrasados90Dias = new ArrayList<>();
        List<DetalheCustodiadoAtrasado> atrasadosMais90Dias = new ArrayList<>();

        long totalAtrasados30Dias = 0;
        long totalAtrasados60Dias = 0;
        long totalAtrasados90Dias = 0;
        long totalAtrasadosMais90Dias = 0;

        Map<String, Long> distribuicaoAtrasos = new HashMap<>();
        distribuicaoAtrasos.put("1-7 dias", 0L);
        distribuicaoAtrasos.put("8-15 dias", 0L);
        distribuicaoAtrasos.put("16-30 dias", 0L);
        distribuicaoAtrasos.put("31-60 dias", 0L);
        distribuicaoAtrasos.put("61-90 dias", 0L);
        distribuicaoAtrasos.put("Mais de 90 dias", 0L);

        DetalheCustodiadoAtrasado maiorAtrasoTemp = null;
        long maiorDiasAtraso = 0;
        long somaDiasAtraso = 0;
        long countAtrasados = 0;

        for (Custodiado custodiado : custodiadosInadimplentes) {
            if (custodiado.getProximoComparecimento() != null &&
                    custodiado.getProximoComparecimento().isBefore(hoje)) {

                long diasAtraso = ChronoUnit.DAYS.between(custodiado.getProximoComparecimento(), hoje);
                somaDiasAtraso += diasAtraso;
                countAtrasados++;

                // Obter endereço de forma segura
                String enderecoAtual = "Não informado";
                try {
                    if (custodiado.getHistoricoEnderecos() != null &&
                            !custodiado.getHistoricoEnderecos().isEmpty()) {
                        enderecoAtual = custodiado.getEnderecoResumido();
                    }
                } catch (Exception e) {
                    log.warn("Erro ao obter endereço do custodiado {}: {}",
                            custodiado.getId(), e.getMessage());
                }

                DetalheCustodiadoAtrasado detalhe = DetalheCustodiadoAtrasado.builder()
                        .id(custodiado.getId())
                        .nome(custodiado.getNome())
                        .processo(custodiado.getProcesso())
                        .periodicidade(custodiado.getPeriodicidadeDescricao())
                        .diasAtraso(diasAtraso)
                        .dataUltimoComparecimento(custodiado.getUltimoComparecimento())
                        .dataProximoComparecimento(custodiado.getProximoComparecimento())
                        .vara(custodiado.getVara())
                        .comarca(custodiado.getComarca())
                        .contato(custodiado.getContato())
                        .enderecoAtual(enderecoAtual)
                        .build();

                // Atualizar maior atraso
                if (diasAtraso > maiorDiasAtraso) {
                    maiorDiasAtraso = diasAtraso;
                    maiorAtrasoTemp = detalhe;
                }

                // Distribuição por faixas
                if (diasAtraso <= 7) {
                    distribuicaoAtrasos.merge("1-7 dias", 1L, Long::sum);
                } else if (diasAtraso <= 15) {
                    distribuicaoAtrasos.merge("8-15 dias", 1L, Long::sum);
                } else if (diasAtraso <= 30) {
                    distribuicaoAtrasos.merge("16-30 dias", 1L, Long::sum);
                } else if (diasAtraso <= 60) {
                    distribuicaoAtrasos.merge("31-60 dias", 1L, Long::sum);
                    totalAtrasados30Dias++;
                    if (atrasados30Dias.size() < 20) {
                        atrasados30Dias.add(detalhe);
                    }
                } else if (diasAtraso <= 90) {
                    distribuicaoAtrasos.merge("61-90 dias", 1L, Long::sum);
                    totalAtrasados60Dias++;
                    if (atrasados60Dias.size() < 20) {
                        atrasados60Dias.add(detalhe);
                    }
                } else {
                    distribuicaoAtrasos.merge("Mais de 90 dias", 1L, Long::sum);
                    totalAtrasadosMais90Dias++;
                    if (atrasadosMais90Dias.size() < 20) {
                        atrasadosMais90Dias.add(detalhe);
                    }
                }

                // Contagem adicional para 90+ dias
                if (diasAtraso > 90) {
                    totalAtrasados90Dias++;
                    if (atrasados90Dias.size() < 20) {
                        atrasados90Dias.add(detalhe);
                    }
                }
            }
        }

        // Calcular média de forma eficiente
        double mediaDiasAtraso = countAtrasados > 0 ? (double) somaDiasAtraso / countAtrasados : 0.0;

        return AnaliseAtrasos.builder()
                .totalCustodiadosAtrasados(custodiadosInadimplentes.size())
                .totalAtrasados30Dias(totalAtrasados30Dias)
                .totalAtrasados60Dias(totalAtrasados60Dias)
                .totalAtrasados90Dias(totalAtrasados90Dias)
                .totalAtrasadosMais90Dias(totalAtrasadosMais90Dias)
                .mediaDiasAtraso(mediaDiasAtraso)
                .distribuicaoAtrasos(distribuicaoAtrasos)
                .custodiadosAtrasados30Dias(atrasados30Dias)
                .custodiadosAtrasados60Dias(atrasados60Dias)
                .custodiadosAtrasados90Dias(atrasados90Dias)
                .custodiadosAtrasadosMais90Dias(atrasadosMais90Dias)
                .custodiadoMaiorAtraso(maiorAtrasoTemp)
                .dataAnalise(hoje)
                .build();
    }

    @Transactional
    public Map<String, Object> migrarCadastrosIniciais(String validadoPor) {
        log.info("Iniciando migração de cadastros iniciais");

        List<Custodiado> todosCustodiados = custodiadoRepository.findAll();

        if (todosCustodiados.isEmpty()) {
            log.warn("Nenhum custodiado encontrado para migração");
            return Map.of(
                    "status", "warning",
                    "message", "Nenhum custodiado encontrado para migração",
                    "totalCustodiados", 0,
                    "custodiadosMigrados", 0
            );
        }

        int custodiadosMigrados = 0;
        List<String> erros = new ArrayList<>();

        for (Custodiado custodiado : todosCustodiados) {
            try {
                boolean jaTemComparecimento = historicoComparecimentoRepository
                        .existsCadastroInicialPorCustodiado(custodiado.getId());

                if (!jaTemComparecimento) {
                    HistoricoComparecimento cadastroInicial = HistoricoComparecimento.builder()
                            .custodiado(custodiado)
                            .dataComparecimento(custodiado.getDataComparecimentoInicial())
                            .horaComparecimento(LocalTime.now())
                            .tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                            .validadoPor(validadoPor)
                            .observacoes("Cadastro inicial migrado do sistema")
                            .mudancaEndereco(Boolean.FALSE)
                            .build();

                    historicoComparecimentoRepository.save(cadastroInicial);
                    custodiadosMigrados++;

                    log.debug("Cadastro inicial criado para custodiado: {} (ID: {})",
                            custodiado.getNome(), custodiado.getId());
                }

            } catch (Exception e) {
                String erro = String.format("Erro ao migrar custodiado %s (ID: %d): %s",
                        custodiado.getNome(), custodiado.getId(), e.getMessage());
                erros.add(erro);
                log.error(erro, e);
            }
        }

        Map<String, Object> resultado = Map.of(
                "status", "success",
                "message", String.format("Migração concluída. %d de %d custodiados migrados",
                        custodiadosMigrados, todosCustodiados.size()),
                "totalCustodiados", todosCustodiados.size(),
                "custodiadosMigrados", custodiadosMigrados,
                "custodiadosJaComCadastro", todosCustodiados.size() - custodiadosMigrados - erros.size(),
                "erros", erros.size(),
                "detalhesErros", erros
        );

        log.info("Migração concluída - Resultado: {}", resultado);
        return resultado;
    }

    // MÉTODOS PRIVADOS

    private void limparEFormatarDadosDTO(ComparecimentoDTO dto) {
        if (dto.getValidadoPor() != null) {
            dto.setValidadoPor(dto.getValidadoPor().trim());
        }

        if (dto.getObservacoes() != null) {
            dto.setObservacoes(dto.getObservacoes().trim());
            if (dto.getObservacoes().isEmpty()) {
                dto.setObservacoes(null);
            }
        }

        if (dto.getMotivoMudancaEndereco() != null) {
            dto.setMotivoMudancaEndereco(dto.getMotivoMudancaEndereco().trim());
            if (dto.getMotivoMudancaEndereco().isEmpty()) {
                dto.setMotivoMudancaEndereco(null);
            }
        }

        if (dto.getDataComparecimento() != null && dto.getDataComparecimento().isAfter(LocalDate.now())) {
            log.warn("Data de comparecimento futura ajustada para hoje: {}", dto.getDataComparecimento());
            dto.setDataComparecimento(LocalDate.now());
        }

        if (dto.houveMudancaEndereco() && dto.getNovoEndereco() != null) {
            var endereco = dto.getNovoEndereco();

            if (endereco.getCep() != null) {
                endereco.setCep(endereco.getCep().replaceAll("[^\\d]", ""));
                if (endereco.getCep().length() == 8) {
                    endereco.setCep(endereco.getCep().substring(0, 5) + "-" + endereco.getCep().substring(5));
                }
            }

            if (endereco.getLogradouro() != null) endereco.setLogradouro(endereco.getLogradouro().trim());
            if (endereco.getBairro() != null) endereco.setBairro(endereco.getBairro().trim());
            if (endereco.getCidade() != null) endereco.setCidade(endereco.getCidade().trim());
            if (endereco.getEstado() != null) endereco.setEstado(endereco.getEstado().trim().toUpperCase());
        }
    }

    private void validarComparecimento(ComparecimentoDTO dto) {
        if (dto.getCustodiadoId() == null || dto.getCustodiadoId() <= 0) {
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        }

        if (dto.getDataComparecimento() == null) {
            throw new IllegalArgumentException("Data do comparecimento é obrigatória");
        }

        if (dto.getDataComparecimento().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Data do comparecimento não pode ser futura");
        }

        if (dto.getTipoValidacao() == null) {
            throw new IllegalArgumentException("Tipo de validação é obrigatório");
        }

        if (dto.getValidadoPor() == null || dto.getValidadoPor().trim().isEmpty()) {
            throw new IllegalArgumentException("Validado por é obrigatório");
        }

        if (dto.houveMudancaEndereco()) {
            if (dto.getNovoEndereco() == null) {
                throw new IllegalArgumentException("Dados do novo endereço são obrigatórios quando há mudança");
            }

            if (!dto.getNovoEndereco().isCompleto()) {
                throw new IllegalArgumentException("Todos os campos obrigatórios do novo endereço devem ser preenchidos");
            }
        }

        validarDuplicidadeComparecimento(dto);
    }

    private void validarDuplicidadeComparecimento(ComparecimentoDTO dto) {
        List<HistoricoComparecimento> comparecimentosMesmaData =
                historicoComparecimentoRepository.findByCustodiado_IdAndDataComparecimento(
                        dto.getCustodiadoId(), dto.getDataComparecimento());

        if (!comparecimentosMesmaData.isEmpty()) {
            boolean temCadastroInicial = comparecimentosMesmaData.stream()
                    .anyMatch(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL);

            if (temCadastroInicial && dto.getTipoValidacao() != TipoValidacao.CADASTRO_INICIAL) {
                return;
            }

            if (!temCadastroInicial || dto.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL) {
                throw new IllegalArgumentException("Já existe comparecimento registrado para este custodiado na data: " +
                        dto.getDataComparecimento());
            }
        }
    }

    private HistoricoComparecimento criarHistoricoComparecimento(ComparecimentoDTO dto, Custodiado custodiado) {
        return HistoricoComparecimento.builder()
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
    }

    private void processarMudancaEndereco(ComparecimentoDTO dto, Custodiado custodiado, HistoricoComparecimento historico) {
        log.info("Processando mudança de endereço - Custodiado: {}", custodiado.getNome());

        int enderecosDesativados = historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());

        if (enderecosDesativados > 0) {
            log.info("Desativados {} endereço(s) anterior(es) do custodiado ID: {}",
                    enderecosDesativados, custodiado.getId());
        }

        criarNovoHistoricoEndereco(custodiado, dto, historico);
    }

    private void criarNovoHistoricoEndereco(Custodiado custodiado, ComparecimentoDTO dto, HistoricoComparecimento historico) {
        long enderecosAtivos = historicoEnderecoRepository.countEnderecosAtivosPorCustodiado(custodiado.getId());

        if (enderecosAtivos > 0) {
            log.warn("ATENÇÃO: Ainda existem {} endereços ativos para custodiado {}. Desativando...",
                    enderecosAtivos, custodiado.getId());
            historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());
        }

        HistoricoEndereco novoHistorico = HistoricoEndereco.builder()
                .custodiado(custodiado)
                .cep(dto.getNovoEndereco().getCep())
                .logradouro(dto.getNovoEndereco().getLogradouro())
                .numero(dto.getNovoEndereco().getNumero())
                .complemento(dto.getNovoEndereco().getComplemento())
                .bairro(dto.getNovoEndereco().getBairro())
                .cidade(dto.getNovoEndereco().getCidade())
                .estado(dto.getNovoEndereco().getEstado())
                .dataInicio(dto.getDataComparecimento())
                .dataFim(null)
                .ativo(Boolean.TRUE)
                .motivoAlteracao(dto.getMotivoMudancaEndereco())
                .validadoPor(dto.getValidadoPor())
                .historicoComparecimento(historico)
                .build();

        HistoricoEndereco enderecoSalvo = historicoEnderecoRepository.save(novoHistorico);
        historicoEnderecoRepository.desativarOutrosEnderecosAtivos(custodiado.getId(), enderecoSalvo.getId());
        adicionarEnderecoAoHistorico(historico, enderecoSalvo);

        log.info("Novo histórico de endereço criado - ID: {}, Endereço: {}, É o único ativo: true",
                enderecoSalvo.getId(), enderecoSalvo.getEnderecoResumido());
    }

    private void adicionarEnderecoAoHistorico(HistoricoComparecimento historico, HistoricoEndereco endereco) {
        if (historico.getEnderecosAlterados() == null) {
            historico.setEnderecosAlterados(new ArrayList<>());
        }

        historico.getEnderecosAlterados().add(endereco);
        endereco.setHistoricoComparecimento(historico);
        historico.setMudancaEndereco(Boolean.TRUE);

        log.debug("Endereço adicionado ao histórico - Histórico ID: {}, Endereço ID: {}",
                historico.getId(), endereco.getId());
    }

    private void atualizarDadosCustodiado(Custodiado custodiado, ComparecimentoDTO dto) {
        if (dto.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL) {
            return;
        }

        custodiado.setUltimoComparecimento(dto.getDataComparecimento());
        custodiado.calcularProximoComparecimento();
        custodiado.atualizarStatusBaseadoEmData();
        custodiadoRepository.save(custodiado);

        log.info("Dados do custodiado atualizados - Último comparecimento: {}, Próximo: {}, Status: {}",
                custodiado.getUltimoComparecimento(), custodiado.getProximoComparecimento(), custodiado.getStatus());
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
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

    @lombok.Data
    @lombok.Builder
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

    @lombok.Data
    @lombok.Builder
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

    @lombok.Data
    @lombok.Builder
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

    @lombok.Data
    @lombok.Builder
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

    @lombok.Data
    @lombok.Builder
    public static class ProximosComparecimentos {
        private int diasAnalisados;
        private long totalPrevistoProximosDias;
        private long totalAtrasados;
        private long comparecimentosHoje;
        private long comparecimentosAmanha;
        private List<ComparecimentoDiario> detalhesPorDia;
        private List<DetalheCustodiado> custodiadosAtrasados;
    }

    @lombok.Data
    @lombok.Builder
    public static class ComparecimentoDiario {
        private LocalDate data;
        private String diaSemana;
        private int totalPrevisto;
        private List<DetalheCustodiado> custodiados;
    }

    @lombok.Data
    @lombok.Builder
    public static class DetalheCustodiado {
        private Long id;
        private String nome;
        private String processo;
        private String periodicidade;
        private long diasAtraso;
    }

    @lombok.Data
    @lombok.Builder
    public static class AnaliseComparecimentos {
        private long comparecimentosUltimos30Dias;
        private long comparecimentosOnlineUltimos30Dias;
        private long comparecimentosPresenciaisUltimos30Dias;
        private double taxaOnlineUltimos30Dias;
        private Map<String, Long> comparecimentosPorDiaSemana;
        private Map<Integer, Long> comparecimentosPorHora;
    }

    @lombok.Data
    @lombok.Builder
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

    @lombok.Data
    @lombok.Builder
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