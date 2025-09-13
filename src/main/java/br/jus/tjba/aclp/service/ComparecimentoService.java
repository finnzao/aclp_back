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

    private final PessoaRepository pessoaRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final EnderecoRepository enderecoRepository;

    /**
     * Registra um novo comparecimento (presencial ou online)
     */
    @Transactional
    public HistoricoComparecimento registrarComparecimento(ComparecimentoDTO dto) {
        log.info("Iniciando registro de comparecimento - Pessoa ID: {}, Tipo: {}",
                dto.getPessoaId(), dto.getTipoValidacao());

        // Limpar e validar dados
        dto.limparEFormatarDados();
        validarComparecimento(dto);

        // Buscar pessoa
        Pessoa pessoa = pessoaRepository.findById(dto.getPessoaId())
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada com ID: " + dto.getPessoaId()));

        // Criar histórico de comparecimento
        HistoricoComparecimento historico = criarHistoricoComparecimento(dto, pessoa);

        // Processar mudança de endereço se necessário
        if (dto.houveMudancaEndereco()) {
            processarMudancaEndereco(dto, pessoa, historico);
        }

        // Salvar histórico
        HistoricoComparecimento historicoSalvo = historicoComparecimentoRepository.save(historico);

        // Atualizar dados da pessoa
        atualizarDadosPessoa(pessoa, dto);

        log.info("Comparecimento registrado com sucesso - ID: {}, Pessoa: {}, Mudança endereço: {}",
                historicoSalvo.getId(), pessoa.getNome(), dto.houveMudancaEndereco());

        return historicoSalvo;
    }

    /**
     * Registra comparecimento inicial (cadastro)
     */
    @Transactional
    public HistoricoComparecimento registrarComparecimentoInicial(Pessoa pessoa, String validadoPor) {
        log.info("Registrando comparecimento inicial - Pessoa: {}", pessoa.getNome());

        ComparecimentoDTO dto = ComparecimentoDTO.builder()
                .pessoaId(pessoa.getId())
                .dataComparecimento(pessoa.getDataComparecimentoInicial())
                .horaComparecimento(LocalTime.now())
                .tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                .observacoes("Cadastro inicial no sistema")
                .validadoPor(validadoPor)
                .mudancaEndereco(Boolean.FALSE)
                .build();

        HistoricoComparecimento historico = criarHistoricoComparecimento(dto, pessoa);

        // Criar histórico do endereço inicial
        criarHistoricoEnderecoInicial(pessoa, historico);

        return historicoComparecimentoRepository.save(historico);
    }

    /**
     * Busca histórico de comparecimentos de uma pessoa
     */
    @Transactional(readOnly = true)
    public List<HistoricoComparecimento> buscarHistoricoPorPessoa(Long pessoaId) {
        log.info("Buscando histórico de comparecimentos - Pessoa ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        return historicoComparecimentoRepository.findByPessoaIdOrderByDataComparecimentoDesc(pessoaId);
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
    public List<HistoricoComparecimento> buscarComparecimentosComMudancaEndereco(Long pessoaId) {
        log.info("Buscando comparecimentos com mudança de endereço - Pessoa ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        return historicoComparecimentoRepository.findByPessoaIdAndMudancaEnderecoTrue(pessoaId);
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

    // ========== MÉTODOS PRIVADOS ==========

    private void validarComparecimento(ComparecimentoDTO dto) {
        // Validar dados básicos
        if (dto.getPessoaId() == null || dto.getPessoaId() <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
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
                historicoComparecimentoRepository.findByPessoaIdAndDataComparecimento(
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
                throw new IllegalArgumentException("Já existe comparecimento registrado para esta pessoa na data: " +
                        dto.getDataComparecimento());
            }
        }
    }

    private HistoricoComparecimento criarHistoricoComparecimento(ComparecimentoDTO dto, Pessoa pessoa) {
        return HistoricoComparecimento.builder()
                .pessoa(pessoa)
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

    private void processarMudancaEndereco(ComparecimentoDTO dto, Pessoa pessoa, HistoricoComparecimento historico) {
        log.info("Processando mudança de endereço - Pessoa: {}", pessoa.getNome());

        // 1. Finalizar endereço anterior no histórico
        finalizarEnderecoAnterior(pessoa, dto.getDataComparecimento());

        // 2. Atualizar endereço atual na tabela enderecos
        atualizarEnderecoAtual(pessoa, dto.getNovoEndereco());

        // 3. Criar novo registro no histórico de endereços
        criarNovoHistoricoEndereco(pessoa, dto, historico);
    }

    private void finalizarEnderecoAnterior(Pessoa pessoa, LocalDate dataFim) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorPessoa(pessoa.getId());

        enderecoAtivo.ifPresent(endereco -> {
            endereco.setDataFim(dataFim);
            historicoEnderecoRepository.save(endereco);
            log.info("Endereço anterior finalizado - ID: {}, Data fim: {}", endereco.getId(), dataFim);
        });
    }

    private void atualizarEnderecoAtual(Pessoa pessoa, ComparecimentoDTO.EnderecoDTO novoEnderecoDTO) {
        Endereco enderecoAtual = pessoa.getEndereco();

        // Atualizar dados do endereço atual
        enderecoAtual.setCep(novoEnderecoDTO.getCep());
        enderecoAtual.setLogradouro(novoEnderecoDTO.getLogradouro());
        enderecoAtual.setNumero(novoEnderecoDTO.getNumero());
        enderecoAtual.setComplemento(novoEnderecoDTO.getComplemento());
        enderecoAtual.setBairro(novoEnderecoDTO.getBairro());
        enderecoAtual.setCidade(novoEnderecoDTO.getCidade());
        enderecoAtual.setEstado(novoEnderecoDTO.getEstado());

        enderecoRepository.save(enderecoAtual);
        log.info("Endereço atual atualizado - Pessoa: {}, Novo endereço: {}",
                pessoa.getNome(), enderecoAtual.getEnderecoResumido());
    }

    private void criarNovoHistoricoEndereco(Pessoa pessoa, ComparecimentoDTO dto, HistoricoComparecimento historico) {
        HistoricoEndereco novoHistorico = HistoricoEndereco.builder()
                .pessoa(pessoa)
                .cep(dto.getNovoEndereco().getCep())
                .logradouro(dto.getNovoEndereco().getLogradouro())
                .numero(dto.getNovoEndereco().getNumero())
                .complemento(dto.getNovoEndereco().getComplemento())
                .bairro(dto.getNovoEndereco().getBairro())
                .cidade(dto.getNovoEndereco().getCidade())
                .estado(dto.getNovoEndereco().getEstado())
                .dataInicio(dto.getDataComparecimento())
                .dataFim(null) // Endereço ativo
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

    private void criarHistoricoEnderecoInicial(Pessoa pessoa, HistoricoComparecimento historico) {
        Endereco enderecoAtual = pessoa.getEndereco();

        HistoricoEndereco historicoInicial = HistoricoEndereco.builder()
                .pessoa(pessoa)
                .cep(enderecoAtual.getCep())
                .logradouro(enderecoAtual.getLogradouro())
                .numero(enderecoAtual.getNumero())
                .complemento(enderecoAtual.getComplemento())
                .bairro(enderecoAtual.getBairro())
                .cidade(enderecoAtual.getCidade())
                .estado(enderecoAtual.getEstado())
                .dataInicio(pessoa.getDataComparecimentoInicial())
                .dataFim(null) // Endereço ativo
                .motivoAlteracao("Endereço inicial no cadastro")
                .validadoPor(historico.getValidadoPor())
                .historicoComparecimento(historico)
                .build();

        historicoEnderecoRepository.save(historicoInicial);

        log.info("Histórico de endereço inicial criado - Pessoa: {}, Endereço: {}",
                pessoa.getNome(), historicoInicial.getEnderecoResumido());
    }

    private void atualizarDadosPessoa(Pessoa pessoa, ComparecimentoDTO dto) {
        // Não atualizar para cadastro inicial
        if (dto.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL) {
            return;
        }

        // Atualizar último comparecimento
        pessoa.setUltimoComparecimento(dto.getDataComparecimento());

        // Calcular próximo comparecimento
        pessoa.calcularProximoComparecimento();

        // Atualizar status para EM_CONFORMIDADE se estava inadimplente
        if (pessoa.getStatus() == StatusComparecimento.INADIMPLENTE) {
            pessoa.setStatus(StatusComparecimento.EM_CONFORMIDADE);
            log.info("Status da pessoa atualizado para EM_CONFORMIDADE - Pessoa: {}", pessoa.getNome());
        }

        pessoaRepository.save(pessoa);

        log.info("Dados da pessoa atualizados - Último comparecimento: {}, Próximo: {}, Status: {}",
                pessoa.getUltimoComparecimento(), pessoa.getProximoComparecimento(), pessoa.getStatus());
    }


    /**
     * Migra pessoas existentes criando seus comparecimentos iniciais
     */
    @Transactional
    public Map<String, Object> migrarCadastrosIniciais(String validadoPor) {
        log.info("Iniciando migração de cadastros iniciais");

        List<Pessoa> todasPessoas = pessoaRepository.findAll();

        if (todasPessoas.isEmpty()) {
            log.warn("Nenhuma pessoa encontrada para migração");
            return Map.of(
                    "status", "warning",
                    "message", "Nenhuma pessoa encontrada para migração",
                    "totalPessoas", 0,
                    "pessoasMigradas", 0
            );
        }

        int pessoasMigradas = 0;
        List<String> erros = new ArrayList<>();

        for (Pessoa pessoa : todasPessoas) {
            try {
                // Verificar se já tem cadastro inicial
                boolean jaTempComparecimento = historicoComparecimentoRepository
                        .existsCadastroInicialPorPessoa(pessoa.getId());

                if (!jaTempComparecimento) {
                    // Criar comparecimento inicial
                    registrarComparecimentoInicial(pessoa, validadoPor);
                    pessoasMigradas++;

                    log.debug("Cadastro inicial criado para pessoa: {} (ID: {})",
                            pessoa.getNome(), pessoa.getId());
                } else {
                    log.debug("Pessoa já possui cadastro inicial: {} (ID: {})",
                            pessoa.getNome(), pessoa.getId());
                }

            } catch (Exception e) {
                String erro = String.format("Erro ao migrar pessoa %s (ID: %d): %s",
                        pessoa.getNome(), pessoa.getId(), e.getMessage());
                erros.add(erro);
                log.error(erro, e);
            }
        }

        Map<String, Object> resultado = Map.of(
                "status", "success",
                "message", String.format("Migração concluída. %d de %d pessoas migradas",
                        pessoasMigradas, todasPessoas.size()),
                "totalPessoas", todasPessoas.size(),
                "pessoasMigradas", pessoasMigradas,
                "pessoasJaComCadastro", todasPessoas.size() - pessoasMigradas - erros.size(),
                "erros", erros.size(),
                "detalhesErros", erros
        );

        log.info("Migração concluída - Resultado: {}", resultado);
        return resultado;
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

    /**
     * Busca estatísticas gerais de todo o sistema
     */
    @Transactional(readOnly = true)
    public EstatisticasGerais buscarEstatisticasGerais() {
        log.info("Buscando estatísticas gerais do sistema");

        List<HistoricoComparecimento> todosComparecimentos =
                historicoComparecimentoRepository.findAll();

        if (todosComparecimentos.isEmpty()) {
            log.warn("Nenhum comparecimento encontrado no sistema");
            return criarEstatisticasVazias();
        }

        // Encontrar período total
        LocalDate menorData = todosComparecimentos.stream()
                .map(HistoricoComparecimento::getDataComparecimento)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate maiorData = todosComparecimentos.stream()
                .map(HistoricoComparecimento::getDataComparecimento)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // Calcular estatísticas
        long totalComparecimentos = todosComparecimentos.size();
        long comparecimentosPresenciais = todosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.PRESENCIAL)
                .count();
        long comparecimentosOnline = todosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.ONLINE)
                .count();
        long cadastrosIniciais = todosComparecimentos.stream()
                .filter(h -> h.getTipoValidacao() == TipoValidacao.CADASTRO_INICIAL)
                .count();
        long mudancasEndereco = todosComparecimentos.stream()
                .filter(HistoricoComparecimento::houveMudancaEndereco)
                .count();

        return EstatisticasGerais.builder()
                .periodo(menorData + " a " + maiorData)
                .dataInicio(menorData)
                .dataFim(maiorData)
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
     * Busca resumo completo do sistema
     */
    @Transactional(readOnly = true)
    public ResumoSistema buscarResumoSistema() {
        log.info("Buscando resumo completo do sistema");

        // Estatísticas de pessoas
        long totalPessoas = pessoaRepository.count();
        long pessoasEmConformidade = pessoaRepository.countByStatus(StatusComparecimento.EM_CONFORMIDADE);
        long pessoasInadimplentes = pessoaRepository.countByStatus(StatusComparecimento.INADIMPLENTE);

        // Pessoas com comparecimento hoje
        long comparecimentosHoje = pessoaRepository.findByProximoComparecimento(LocalDate.now()).size();

        // Pessoas em atraso
        long pessoasAtrasadas = pessoaRepository.findByProximoComparecimentoBefore(LocalDate.now()).size();

        // Estatísticas de comparecimentos
        EstatisticasGerais estatisticasComparecimentos = buscarEstatisticasGerais();

        return ResumoSistema.builder()
                .totalPessoas(totalPessoas)
                .pessoasEmConformidade(pessoasEmConformidade)
                .pessoasInadimplentes(pessoasInadimplentes)
                .comparecimentosHoje(comparecimentosHoje)
                .pessoasAtrasadas(pessoasAtrasadas)
                .percentualConformidade(totalPessoas > 0 ?
                        (double) pessoasEmConformidade / totalPessoas * 100 : 0.0)
                .estatisticasComparecimentos(estatisticasComparecimentos)
                .dataConsulta(LocalDate.now())
                .build();
    }

    /**
     * Cria estatísticas vazias quando não há dados
     */
    private EstatisticasGerais criarEstatisticasVazias() {
        return EstatisticasGerais.builder()
                .periodo("Nenhum dado encontrado")
                .dataInicio(LocalDate.now())
                .dataFim(LocalDate.now())
                .totalComparecimentos(0L)
                .comparecimentosPresenciais(0L)
                .comparecimentosOnline(0L)
                .cadastrosIniciais(0L)
                .mudancasEndereco(0L)
                .percentualPresencial(0.0)
                .percentualOnline(0.0)
                .build();
    }



    /**
     * DTO para estatísticas gerais (sem necessidade de especificar período)
     */
    @lombok.Data
    @lombok.Builder
    public static class EstatisticasGerais {
        private String periodo;
        private LocalDate dataInicio;
        private LocalDate dataFim;
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long cadastrosIniciais;
        private long mudancasEndereco;
        private double percentualPresencial;
        private double percentualOnline;
    }

    /**
     * DTO para resumo completo do sistema
     */
    @lombok.Data
    @lombok.Builder
    public static class ResumoSistema {
        private long totalPessoas;
        private long pessoasEmConformidade;
        private long pessoasInadimplentes;
        private long comparecimentosHoje;
        private long pessoasAtrasadas;
        private double percentualConformidade;
        private EstatisticasGerais estatisticasComparecimentos;
        private LocalDate dataConsulta;
    }
}