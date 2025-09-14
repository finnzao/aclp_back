package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.model.*;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparecimentoService {

    private final CustodiadoRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;

    /**
     * Registra um novo comparecimento (presencial ou online)
     */
    @Transactional
    public HistoricoComparecimento registrarComparecimento(ComparecimentoDTO dto) {
        log.info("Iniciando registro de comparecimento - Custodiado ID: {}, Tipo: {}",
                dto.getCustodiadoId(), dto.getTipoValidacao());

        // Limpar e validar dados
        dto.limparEFormatarDados();
        validarComparecimento(dto);

        // Buscar custodiado
        Custodiado custodiado = custodiadoRepository.findById(dto.getCustodiadoId())
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + dto.getCustodiadoId()));

        // Criar histórico de comparecimento
        HistoricoComparecimento historico = criarHistoricoComparecimento(dto, custodiado);

        // Processar mudança de endereço se necessário
        if (dto.houveMudancaEndereco()) {
            processarMudancaEndereco(dto, custodiado, historico);
        }

        // Salvar histórico
        HistoricoComparecimento historicoSalvo = historicoComparecimentoRepository.save(historico);

        // Atualizar dados do custodiado
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

    private void validarComparecimento(ComparecimentoDTO dto) {
        // Validar dados básicos
        if (dto.getPessoaId() == null || dto.getPessoaId() <= 0) {
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
                        dto.getPessoaId(), dto.getDataComparecimento());

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

        // 2. Criar novo registro no histórico de endereços
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
                .historicoComparecimento(historico)
                .build();

        historicoEnderecoRepository.save(novoHistorico);

        // Adicionar ao histórico de comparecimento
        historico.adicionarEnderecoAlterado(novoHistorico);

        log.info("Novo histórico de endereço criado - ID: {}, Endereço: {}",
                novoHistorico.getId(), novoHistorico.getEnderecoResumido());
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

    /**
     * Busca estatísticas de comparecimentos
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
     * DTO para estatísticas de comparecimentos
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
}