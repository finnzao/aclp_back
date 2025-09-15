package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
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

    /**
     * Registra um novo comparecimento (presencial ou online)
     * SOLUÇÃO 1: Ordem correta - salvar histórico ANTES de referenciar
     */
    @Transactional
    public HistoricoComparecimento registrarComparecimento(ComparecimentoDTO dto) {
        log.info("Iniciando registro de comparecimento - Custodiado ID: {}, Tipo: {}",
                dto.getCustodiadoId(), dto.getTipoValidacao());

        // Limpar e validar dados
        limparEFormatarDadosDTO(dto);
        validarComparecimento(dto);

        // Buscar custodiado
        Custodiado custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + dto.getCustodiadoId()));

        // 1. Criar histórico de comparecimento
        HistoricoComparecimento historico = criarHistoricoComparecimento(dto, custodiado);

        // 2. ✅ SALVAR HISTÓRICO PRIMEIRO (antes de referenciar em outros objetos)
        HistoricoComparecimento historicoSalvo = historicoComparecimentoRepository.save(historico);

        // 3. Processar mudança de endereço DEPOIS (agora historico já existe no banco)
        if (dto.houveMudancaEndereco()) {
            processarMudancaEndereco(dto, custodiado, historicoSalvo);

            // 4. Atualizar histórico com endereços (se necessário)
            historicoSalvo = historicoComparecimentoRepository.save(historicoSalvo);
        }

        // 5. Atualizar dados do custodiado
        atualizarDadosCustodiado(custodiado, dto);

        log.info("Comparecimento registrado com sucesso - ID: {}, Custodiado: {}, Mudança endereço: {}",
                historicoSalvo.getId(), custodiado.getNome(), dto.houveMudancaEndereco());

        return historicoSalvo;
    }

    /**
     * Busca histórico de comparecimentos de um custodiado
     */
    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarHistoricoPorCustodiado(Long custodiadoId) {
        log.info("Buscando histórico de comparecimentos - Custodiado ID: {}", custodiadoId);

        if (custodiadoId == null || custodiadoId <= 0) {
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        }

        return historicoComparecimentoRepository.findByCustodiadoIdOrderByDataComparecimentoDesc(custodiadoId);
    }

    /**
     * Busca comparecimentos por período
     */
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

    /**
     * Busca comparecimentos com mudança de endereço
     */
    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarComparecimentosComMudancaEndereco(Long custodiadoId) {
        log.info("Buscando comparecimentos com mudança de endereço - Custodiado ID: {}", custodiadoId);

        if (custodiadoId == null || custodiadoId <= 0) {
            throw new IllegalArgumentException("ID do custodiado deve ser um número positivo");
        }

        return historicoComparecimentoRepository.findByCustodiadoIdAndMudancaEnderecoTrue(custodiadoId);
    }

    /**
     * Busca comparecimentos de hoje
     */
    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarComparecimentosHoje() {
        log.info("Buscando comparecimentos de hoje");
        return historicoComparecimentoRepository.findByDataComparecimento(LocalDate.now());
    }

    /**
     * Atualiza observações de um comparecimento
     */
    @Transactional
    public HistoricoComparecimento atualizarObservacoes(Long historicoId, String observacoes) {
        log.info("Atualizando observações do comparecimento ID: {}", historicoId);

        HistoricoComparecimento historico = historicoComparecimentoRepository.findById(historicoId)
                .orElseThrow(() -> new EntityNotFoundException("Histórico de comparecimento não encontrado com ID: " + historicoId));

        historico.setObservacoes(observacoes != null ? observacoes.trim() : null);

        return historicoComparecimentoRepository.save(historico);
    }

    /**
     * Busca estatísticas de comparecimentos por período
     */
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

    /**
     * Busca estatísticas gerais de todos os comparecimentos
     */
    @Transactional(readOnly = true)
    public EstatisticasGerais buscarEstatisticasGerais() {
        log.info("Buscando estatísticas gerais de comparecimentos");

        // Total de comparecimentos
        long totalComparecimentos = historicoComparecimentoRepository.count();

        // Comparecimentos por tipo
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

        // Mudanças de endereço
        long totalMudancasEndereco = todosComparecimentos.stream()
                .filter(HistoricoComparecimento::houveMudancaEndereco)
                .count();

        // Comparecimentos hoje
        long comparecimentosHoje = historicoComparecimentoRepository
                .findByDataComparecimento(LocalDate.now()).size();

        // Comparecimentos este mês
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = LocalDate.now();
        long comparecimentosEsteMes = historicoComparecimentoRepository
                .findByDataComparecimentoBetween(inicioMes, fimMes).size();

        // Custodiados únicos com comparecimento
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
     * Busca resumo completo do sistema com análises avançadas
     */
    @Transactional(readOnly = true)
    public ResumoSistema buscarResumoSistema() {
        log.info("Buscando resumo completo do sistema com análises avançadas");

        LocalDate hoje = LocalDate.now();

        // === DADOS BÁSICOS ===

        // Totais de custodiados
        long totalCustodiados = custodiadoRepository.count();
        long custodiadosEmConformidade = custodiadoRepository.countByStatus(StatusComparecimento.EM_CONFORMIDADE);
        long custodiadosInadimplentes = custodiadoRepository.countByStatus(StatusComparecimento.INADIMPLENTE);

        // Custodiados com comparecimento hoje
        long comparecimentosHoje = custodiadoRepository.findComparecimentosHoje().size();

        // Total de comparecimentos
        long totalComparecimentos = historicoComparecimentoRepository.count();

        // Comparecimentos este mês
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        long comparecimentosEsteMes = historicoComparecimentoRepository
                .findByDataComparecimentoBetween(inicioMes, hoje).size();

        // Mudanças de endereço
        long totalMudancasEndereco = historicoComparecimentoRepository
                .count();

        // Endereços ativos
        long enderecosAtivos = historicoEnderecoRepository.findAllEnderecosAtivos().size();

        // Custodiados sem histórico
        long custodiadosSemHistorico = custodiadoRepository.findCustodiadosSemHistorico().size();

        // Custodiados sem endereço ativo
        long custodiadosSemEnderecoAtivo = historicoEnderecoRepository.countCustodiadosSemEnderecoAtivo();

        // Percentuais básicos
        double percentualConformidade = totalCustodiados > 0 ?
                (double) custodiadosEmConformidade / totalCustodiados * 100 : 0.0;
        double percentualInadimplencia = totalCustodiados > 0 ?
                (double) custodiadosInadimplentes / totalCustodiados * 100 : 0.0;

        // === NOVOS DADOS SOLICITADOS ===

        // 1. Relatório dos últimos 6 meses
        RelatorioUltimosMeses relatorioMeses = calcularRelatorioUltimosMeses(6);

        // 2. Tendência de conformidade (últimos 6 meses)
        List<TendenciaMensal> tendenciaConformidade = calcularTendenciaConformidade(6);

        // 3. Comparecimentos próximos 7 dias
        ProximosComparecimentos proximosComparecimentos = calcularProximosComparecimentos(7);

        // 4. Análises adicionais
        AnaliseComparecimentos analiseComparecimentos = calcularAnaliseComparecimentos();

        // 5. NOVO: Análise de atrasos superiores a 30 dias
        AnaliseAtrasos analiseAtrasos = calcularAnaliseAtrasos();

        return ResumoSistema.builder()
                // Dados básicos
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
                // Novos dados
                .relatorioUltimosMeses(relatorioMeses)
                .tendenciaConformidade(tendenciaConformidade)
                .proximosComparecimentos(proximosComparecimentos)
                .analiseComparecimentos(analiseComparecimentos)
                .analiseAtrasos(analiseAtrasos) // NOVO campo adicionado
                .build();
    }

    /**
     * Calcula relatório dos últimos N meses
     */
    private RelatorioUltimosMeses calcularRelatorioUltimosMeses(int meses) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioPeríodo = hoje.minusMonths(meses).withDayOfMonth(1);

        List<HistoricoComparecimento> comparecimentosPeriodo =
                historicoComparecimentoRepository.findByDataComparecimentoBetween(inicioPeríodo, hoje);

        long totalComparecimentosPeriodo = comparecimentosPeriodo.size();
        long comparecimentosPresenciais = comparecimentosPeriodo.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();
        long comparecimentosOnline = comparecimentosPeriodo.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();
        long mudancasEnderecoPeriodo = comparecimentosPeriodo.stream()
                .filter(HistoricoComparecimento::houveMudancaEndereco)
                .count();

        // Calcular média mensal
        double mediaComparecimentosMensal = totalComparecimentosPeriodo / (double) meses;

        return RelatorioUltimosMeses.builder()
                .mesesAnalisados(meses)
                .periodoInicio(inicioPeríodo)
                .periodoFim(hoje)
                .totalComparecimentos(totalComparecimentosPeriodo)
                .comparecimentosPresenciais(comparecimentosPresenciais)
                .comparecimentosOnline(comparecimentosOnline)
                .mudancasEndereco(mudancasEnderecoPeriodo)
                .mediaComparecimentosMensal(mediaComparecimentosMensal)
                .percentualPresencial(totalComparecimentosPeriodo > 0 ?
                        (double) comparecimentosPresenciais / totalComparecimentosPeriodo * 100 : 0.0)
                .percentualOnline(totalComparecimentosPeriodo > 0 ?
                        (double) comparecimentosOnline / totalComparecimentosPeriodo * 100 : 0.0)
                .build();
    }

    /**
     * Calcula tendência de conformidade mensal
     */
    private List<TendenciaMensal> calcularTendenciaConformidade(int meses) {
        List<TendenciaMensal> tendencias = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (int i = meses - 1; i >= 0; i--) {
            YearMonth mesAnalise = YearMonth.now().minusMonths(i);
            LocalDate inicioMes = mesAnalise.atDay(1);
            LocalDate fimMes = mesAnalise.atEndOfMonth();

            // Se o mês é o atual, usar até hoje
            if (mesAnalise.equals(YearMonth.now())) {
                fimMes = hoje;
            }

            // Buscar snapshot do status no final do mês
            long totalCustodiadosNoMes = custodiadoRepository.count(); // Simplificado

            // Contar custodiados em conformidade baseado em comparecimentos do mês
            List<HistoricoComparecimento> comparecimentosMes =
                    historicoComparecimentoRepository.findByDataComparecimentoBetween(inicioMes, fimMes);

            Set<Long> custodiadosQueCompareceram = comparecimentosMes.stream()
                    .map(h -> h.getCustodiado().getId())
                    .collect(Collectors.toSet());

            long emConformidade = custodiadosQueCompareceram.size();
            long inadimplentes = totalCustodiadosNoMes - emConformidade;

            double taxaConformidade = totalCustodiadosNoMes > 0 ?
                    (double) emConformidade / totalCustodiadosNoMes * 100 : 0.0;
            double taxaInadimplencia = totalCustodiadosNoMes > 0 ?
                    (double) inadimplentes / totalCustodiadosNoMes * 100 : 0.0;

            tendencias.add(TendenciaMensal.builder()
                    .mes(mesAnalise.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .mesNome(mesAnalise.format(DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR"))))
                    .totalCustodiados(totalCustodiadosNoMes)
                    .emConformidade(emConformidade)
                    .inadimplentes(inadimplentes)
                    .taxaConformidade(taxaConformidade)
                    .taxaInadimplencia(taxaInadimplencia)
                    .totalComparecimentos((long) comparecimentosMes.size())
                    .build());
        }

        return tendencias;
    }

    /**
     * Calcula comparecimentos previstos para os próximos dias
     */
    private ProximosComparecimentos calcularProximosComparecimentos(int dias) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = hoje.plusDays(dias);

        List<Custodiado> custodiadosComComparecimento =
                custodiadoRepository.findByProximoComparecimentoBetween(hoje, dataLimite);

        // Agrupar por data
        Map<LocalDate, List<Custodiado>> comparecimentosPorDia = new TreeMap<>();
        for (Custodiado custodiado : custodiadosComComparecimento) {
            LocalDate dataComparecimento = custodiado.getProximoComparecimento();
            comparecimentosPorDia.computeIfAbsent(dataComparecimento, k -> new ArrayList<>()).add(custodiado);
        }

        // Criar lista de detalhes por dia
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

        // Identificar custodiados em atraso que deveriam comparecer
        List<Custodiado> custodiadosAtrasados = custodiadoRepository.findByProximoComparecimentoBefore(hoje);

        return ProximosComparecimentos.builder()
                .diasAnalisados(dias)
                .totalPrevistoProximosDias(custodiadosComComparecimento.size())
                .totalAtrasados(custodiadosAtrasados.size())
                .comparecimentosHoje(comparecimentosPorDia.getOrDefault(hoje, new ArrayList<>()).size())
                .comparecimentosAmanha(comparecimentosPorDia.getOrDefault(hoje.plusDays(1), new ArrayList<>()).size())
                .detalhesPorDia(detalhesPorDia)
                .custodiadosAtrasados(custodiadosAtrasados.stream()
                        .limit(10) // Limitar para não ficar muito grande
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
     * Calcula análises adicionais de comparecimentos
     */
    private AnaliseComparecimentos calcularAnaliseComparecimentos() {
        LocalDate hoje = LocalDate.now();

        // Taxa de comparecimento online vs presencial nos últimos 30 dias
        LocalDate inicio30Dias = hoje.minusDays(30);
        List<HistoricoComparecimento> comparecimentos30Dias =
                historicoComparecimentoRepository.findByDataComparecimentoBetween(inicio30Dias, hoje);

        long online30 = comparecimentos30Dias.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();
        long presencial30 = comparecimentos30Dias.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();

        // Picos de comparecimento (dias da semana com mais comparecimentos)
        Map<String, Long> comparecimentosPorDiaSemana = comparecimentos30Dias.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getDataComparecimento().getDayOfWeek().toString(),
                        Collectors.counting()
                ));

        // Horários mais comuns (se houver registro de hora)
        Map<Integer, Long> comparecimentosPorHora = comparecimentos30Dias.stream()
                .filter(h -> h.getHoraComparecimento() != null)
                .collect(Collectors.groupingBy(
                        h -> h.getHoraComparecimento().getHour(),
                        Collectors.counting()
                ));

        return AnaliseComparecimentos.builder()
                .comparecimentosUltimos30Dias(comparecimentos30Dias.size())
                .comparecimentosOnlineUltimos30Dias(online30)
                .comparecimentosPresenciaisUltimos30Dias(presencial30)
                .taxaOnlineUltimos30Dias(comparecimentos30Dias.size() > 0 ?
                        (double) online30 / comparecimentos30Dias.size() * 100 : 0.0)
                .comparecimentosPorDiaSemana(comparecimentosPorDiaSemana)
                .comparecimentosPorHora(comparecimentosPorHora)
                .build();
    }

    /**
     * NOVO MÉTODO: Calcula análise de atrasos superiores a 30 dias
     */
    private AnaliseAtrasos calcularAnaliseAtrasos() {
        LocalDate hoje = LocalDate.now();

        // Buscar todos os custodiados inadimplentes
        List<Custodiado> custodiadosInadimplentes = custodiadoRepository.findInadimplentes();

        // Categorizar por período de atraso
        List<DetalheCustodiadoAtrasado> atrasados30Dias = new ArrayList<>();
        List<DetalheCustodiadoAtrasado> atrasados60Dias = new ArrayList<>();
        List<DetalheCustodiadoAtrasado> atrasados90Dias = new ArrayList<>();
        List<DetalheCustodiadoAtrasado> atrasadosMais90Dias = new ArrayList<>();

        long totalAtrasados30Dias = 0;
        long totalAtrasados60Dias = 0;
        long totalAtrasados90Dias = 0;
        long totalAtrasadosMais90Dias = 0;

        // Estatísticas por faixa de atraso
        Map<String, Long> distribuicaoAtrasos = new HashMap<>();
        distribuicaoAtrasos.put("1-7 dias", 0L);
        distribuicaoAtrasos.put("8-15 dias", 0L);
        distribuicaoAtrasos.put("16-30 dias", 0L);
        distribuicaoAtrasos.put("31-60 dias", 0L);
        distribuicaoAtrasos.put("61-90 dias", 0L);
        distribuicaoAtrasos.put("Mais de 90 dias", 0L);

        for (Custodiado custodiado : custodiadosInadimplentes) {
            if (custodiado.getProximoComparecimento() != null &&
                    custodiado.getProximoComparecimento().isBefore(hoje)) {

                long diasAtraso = ChronoUnit.DAYS.between(custodiado.getProximoComparecimento(), hoje);

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
                        .enderecoAtual(custodiado.getEnderecoResumido())
                        .build();

                // Categorizar por período
                if (diasAtraso <= 7) {
                    distribuicaoAtrasos.merge("1-7 dias", 1L, Long::sum);
                } else if (diasAtraso <= 15) {
                    distribuicaoAtrasos.merge("8-15 dias", 1L, Long::sum);
                } else if (diasAtraso <= 30) {
                    distribuicaoAtrasos.merge("16-30 dias", 1L, Long::sum);
                } else if (diasAtraso <= 60) {
                    distribuicaoAtrasos.merge("31-60 dias", 1L, Long::sum);
                    totalAtrasados30Dias++;
                    if (atrasados30Dias.size() < 20) { // Limitar lista para não ficar muito grande
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

                // Adicionar aos atrasados > 30 dias
                if (diasAtraso > 30) {
                    if (diasAtraso > 90) {
                        totalAtrasados90Dias++;
                        if (atrasados90Dias.size() < 20) {
                            atrasados90Dias.add(detalhe);
                        }
                    }
                }
            }
        }

        // Calcular custodiado com maior atraso
        DetalheCustodiadoAtrasado maiorAtraso = custodiadosInadimplentes.stream()
                .filter(c -> c.getProximoComparecimento() != null && c.getProximoComparecimento().isBefore(hoje))
                .map(c -> {
                    long diasAtraso = ChronoUnit.DAYS.between(c.getProximoComparecimento(), hoje);
                    return DetalheCustodiadoAtrasado.builder()
                            .id(c.getId())
                            .nome(c.getNome())
                            .processo(c.getProcesso())
                            .diasAtraso(diasAtraso)
                            .dataUltimoComparecimento(c.getUltimoComparecimento())
                            .dataProximoComparecimento(c.getProximoComparecimento())
                            .build();
                })
                .max(Comparator.comparingLong(DetalheCustodiadoAtrasado::getDiasAtraso))
                .orElse(null);

        // Calcular média de dias de atraso
        double mediaDiasAtraso = custodiadosInadimplentes.stream()
                .filter(c -> c.getProximoComparecimento() != null && c.getProximoComparecimento().isBefore(hoje))
                .mapToLong(c -> ChronoUnit.DAYS.between(c.getProximoComparecimento(), hoje))
                .average()
                .orElse(0.0);

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
                .custodiadoMaiorAtraso(maiorAtraso)
                .dataAnalise(hoje)
                .build();
    }

    /**
     * Migra custodiados existentes criando seus comparecimentos iniciais
     */
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
                // Verificar se já tem cadastro inicial
                boolean jaTemComparecimento = historicoComparecimentoRepository
                        .existsCadastroInicialPorCustodiado(custodiado.getId());

                if (!jaTemComparecimento) {
                    // Criar comparecimento inicial
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
                } else {
                    log.debug("Custodiado já possui cadastro inicial: {} (ID: {})",
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

    // ========== MÉTODOS PRIVADOS ==========

    /**
     * ✅ MÉTODO CRIADO: Limpa e formata dados do DTO
     */
    private void limparEFormatarDadosDTO(ComparecimentoDTO dto) {
        // Limpar strings básicas
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

        // Validar e converter datas se necessário
        if (dto.getDataComparecimento() != null && dto.getDataComparecimento().isAfter(LocalDate.now())) {
            log.warn("Data de comparecimento futura ajustada para hoje: {}", dto.getDataComparecimento());
            dto.setDataComparecimento(LocalDate.now());
        }

        // Limpar dados do novo endereço se houver
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
        // Validar dados básicos
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

        // Validar mudança de endereço
        if (dto.houveMudancaEndereco()) {
            if (dto.getNovoEndereco() == null) {
                throw new IllegalArgumentException("Dados do novo endereço são obrigatórios quando há mudança");
            }

            if (!dto.getNovoEndereco().isCompleto()) {
                throw new IllegalArgumentException("Todos os campos obrigatórios do novo endereço devem ser preenchidos");
            }
        }

        // Validar duplicidade de comparecimento na mesma data
        validarDuplicidadeComparecimento(dto);
    }

    private void validarDuplicidadeComparecimento(ComparecimentoDTO dto) {
        List<HistoricoComparecimento> comparecimentosMesmaData =
                historicoComparecimentoRepository.findByCustodiadoIdAndDataComparecimento(
                        dto.getCustodiadoId(), dto.getDataComparecimento());

        if (!comparecimentosMesmaData.isEmpty()) {
            // Permitir apenas se for cadastro inicial
            boolean temCadastroInicial = comparecimentosMesmaData.stream()
                    .anyMatch(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL);

            if (temCadastroInicial && dto.getTipoValidacao() != TipoValidacao.CADASTRO_INICIAL) {
                // OK - pode ter cadastro inicial + comparecimento regular no mesmo dia
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

        // 1. Finalizar endereço anterior no histórico
        finalizarEnderecoAnterior(custodiado, dto.getDataComparecimento());

        // 2. Criar novo registro no histórico de endereços (historico já foi salvo)
        criarNovoHistoricoEndereco(custodiado, dto, historico);
    }

    private void finalizarEnderecoAnterior(Custodiado custodiado, LocalDate dataFim) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiado.getId());

        enderecoAtivo.ifPresent(endereco -> {
            endereco.finalizarEndereco(dataFim);
            historicoEnderecoRepository.save(endereco);
            log.info("Endereço anterior finalizado - ID: {}, Data fim: {}", endereco.getId(), dataFim);
        });
    }

    private void criarNovoHistoricoEndereco(Custodiado custodiado, ComparecimentoDTO dto, HistoricoComparecimento historico) {
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
                .ativo(Boolean.TRUE)
                .motivoAlteracao(dto.getMotivoMudancaEndereco())
                .validadoPor(dto.getValidadoPor())
                .historicoComparecimento(historico) // ✅ Agora historico já existe no banco
                .build();

        // Salvar endereço
        HistoricoEndereco enderecoSalvo = historicoEnderecoRepository.save(novoHistorico);

        // ✅ USAR MÉTODO AUXILIAR para adicionar endereço
        adicionarEnderecoAoHistorico(historico, enderecoSalvo);

        log.info("Novo histórico de endereço criado - ID: {}, Endereço: {}",
                enderecoSalvo.getId(), enderecoSalvo.getEnderecoResumido());
    }

    /**
     * ✅ MÉTODO CRIADO: Adiciona endereço alterado ao histórico
     * Substitui o método que estava faltando na entidade
     */
    private void adicionarEnderecoAoHistorico(HistoricoComparecimento historico, HistoricoEndereco endereco) {
        // Inicializar lista se necessário
        if (historico.getEnderecosAlterados() == null) {
            historico.setEnderecosAlterados(new ArrayList<>());
        }

        // Adicionar endereço à lista
        historico.getEnderecosAlterados().add(endereco);

        // Configurar relacionamento bidirecional
        endereco.setHistoricoComparecimento(historico);

        // Marcar que houve mudança de endereço
        historico.setMudancaEndereco(Boolean.TRUE);

        log.debug("Endereço adicionado ao histórico - Histórico ID: {}, Endereço ID: {}",
                historico.getId(), endereco.getId());
    }

    private void atualizarDadosCustodiado(Custodiado custodiado, ComparecimentoDTO dto) {
        // Não atualizar para cadastro inicial
        if (dto.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL) {
            return;
        }

        // Atualizar último comparecimento
        custodiado.setUltimoComparecimento(dto.getDataComparecimento());

        // Calcular próximo comparecimento
        custodiado.calcularProximoComparecimento();

        // Atualizar status baseado na data atual
        custodiado.atualizarStatusBaseadoEmData();

        custodiadoRepository.save(custodiado);

        log.info("Dados do custodiado atualizados - Último comparecimento: {}, Próximo: {}, Status: {}",
                custodiado.getUltimoComparecimento(), custodiado.getProximoComparecimento(), custodiado.getStatus());
    }

    // ========== CLASSES INTERNAS - DTOs ==========

    /**
     * DTO para estatísticas de comparecimentos por período
     */
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

    /**
     * DTO para estatísticas gerais do sistema
     */
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

    /**
     * DTO para resumo completo do sistema
     */
    @lombok.Data
    @lombok.Builder
    public static class ResumoSistema {
        // Dados básicos
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

        // Novos dados
        private RelatorioUltimosMeses relatorioUltimosMeses;
        private List<TendenciaMensal> tendenciaConformidade;
        private ProximosComparecimentos proximosComparecimentos;
        private AnaliseComparecimentos analiseComparecimentos;
        private AnaliseAtrasos analiseAtrasos; // NOVO campo
    }

    /**
     * DTO para relatório dos últimos meses
     */
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

    /**
     * DTO para tendência mensal
     */
    @lombok.Data
    @lombok.Builder
    public static class TendenciaMensal {
        private String mes; // formato: yyyy-MM
        private String mesNome; // formato: "Janeiro/2024"
        private long totalCustodiados;
        private long emConformidade;
        private long inadimplentes;
        private double taxaConformidade;
        private double taxaInadimplencia;
        private long totalComparecimentos;
    }

    /**
     * DTO para próximos comparecimentos
     */
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

    /**
     * DTO para comparecimentos diários
     */
    @lombok.Data
    @lombok.Builder
    public static class ComparecimentoDiario {
        private LocalDate data;
        private String diaSemana;
        private int totalPrevisto;
        private List<DetalheCustodiado> custodiados;
    }

    /**
     * DTO para detalhe de custodiado
     */
    @lombok.Data
    @lombok.Builder
    public static class DetalheCustodiado {
        private Long id;
        private String nome;
        private String processo;
        private String periodicidade;
        private long diasAtraso;
    }

    /**
     * DTO para análise de comparecimentos
     */
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

    /**
     * NOVO DTO para análise de atrasos
     */
    @lombok.Data
    @lombok.Builder
    public static class AnaliseAtrasos {
        private long totalCustodiadosAtrasados;
        private long totalAtrasados30Dias; // Atrasados entre 31-60 dias
        private long totalAtrasados60Dias; // Atrasados entre 61-90 dias
        private long totalAtrasados90Dias; // Atrasados há mais de 90 dias
        private long totalAtrasadosMais90Dias; // Atrasados há mais de 90 dias
        private double mediaDiasAtraso;
        private Map<String, Long> distribuicaoAtrasos; // Distribuição por faixas
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados30Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados60Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados90Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasadosMais90Dias;
        private DetalheCustodiadoAtrasado custodiadoMaiorAtraso;
        private LocalDate dataAnalise;
    }

    /**
     *  DTO para detalhe de custodiado atrasado
     */
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