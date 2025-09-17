package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustodiadoService {

    private final CustodiadoRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;

    // ========== MÉTODOS PRINCIPAIS (COMPORTAMENTO ALTERADO) ==========

    @Transactional(readOnly = true)
    public List<Custodiado> findAll() {
        log.info("Buscando todos os custodiados ATIVOS");
        return custodiadoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllIncludingArchived() {
        log.info("Buscando todos os custodiados (ATIVOS + ARQUIVADOS)");
        return custodiadoRepository.findAllIncludingArchived();
    }

    @Transactional(readOnly = true)
    public Optional<Custodiado> findById(Long id) {
        log.info("Buscando custodiado por ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        return custodiadoRepository.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Arquivando custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        if (custodiado.isArquivado()) {
            throw new IllegalArgumentException("Custodiado já está arquivado");
        }

        custodiado.arquivar();
        custodiadoRepository.save(custodiado);

        log.info("Custodiado arquivado com sucesso - ID: {}, Nome: {}", id, custodiado.getNome());
    }

    @Transactional
    public Custodiado reativar(Long id) {
        log.info("Reativando custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        if (!custodiado.isArquivado()) {
            throw new IllegalArgumentException("Custodiado já está ativo");
        }

        validarDuplicidadesDocumentosParaReativacao(custodiado);

        custodiado.reativar();
        Custodiado custodiadoReativado = custodiadoRepository.save(custodiado);

        log.info("Custodiado reativado com sucesso - ID: {}, Nome: {}",
                id, custodiadoReativado.getNome());

        return custodiadoReativado;
    }

    // ========== MÉTODOS EXISTENTES (COMPORTAMENTO MANTIDO) ==========

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcesso(String processo) {
        log.info("Buscando custodiados ATIVOS por processo: {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        return custodiadoRepository.findByProcesso(processo.trim());
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcessoIncludingArchived(String processo) {
        log.info("Buscando todos os custodiados por processo (incluindo arquivados): {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        return custodiadoRepository.findByProcessoIncludingArchived(processo.trim());
    }

    @Transactional
    public Custodiado save(CustodiadoDTO dto) {
        log.info("Iniciando cadastro de novo custodiado - Processo: {}, Nome: {}",
                dto.getProcesso(), dto.getNome());

        try {
            dto.setId(null); // Forçar null para garantir auto-increment

            dto.limparEFormatarDados();
            log.debug("Dados limpos e formatados");

            if (dto.getDataComparecimentoInicial() == null) {
                dto.setDataComparecimentoInicial(LocalDate.now());
                log.info("Data de comparecimento inicial não fornecida - usando data atual: {}",
                        LocalDate.now());
            }

            // Validações
            validarDadosObrigatorios(dto);
            log.debug("Dados obrigatórios validados");

            validarFormatos(dto);
            log.debug("Formatos validados");

            validarDuplicidadesDocumentos(dto);
            log.debug("Duplicidade de documentos validada");

            validarDatasLogicas(dto);
            log.debug("Datas lógicas validadas");

            validarEnderecoCompleto(dto);
            log.debug("Endereço validado");

            Custodiado custodiado = Custodiado.builder()
                    .nome(dto.getNome().trim())
                    .cpf(dto.getCpf())
                    .rg(dto.getRg())
                    .contato(dto.getContato())
                    .processo(dto.getProcesso().trim())
                    .vara(dto.getVara().trim())
                    .comarca(dto.getComarca().trim())
                    .dataDecisao(dto.getDataDecisao())
                    .periodicidade(dto.getPeriodicidade())
                    .dataComparecimentoInicial(dto.getDataComparecimentoInicial())
                    .status(StatusComparecimento.EM_CONFORMIDADE)
                    .situacao(SituacaoCustodiado.ATIVO)
                    .ultimoComparecimento(dto.getDataComparecimentoInicial())
                    .observacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null)
                    .build();

            custodiado.calcularProximoComparecimento();
            log.debug("Próximo comparecimento calculado: {}", custodiado.getProximoComparecimento());

            Custodiado custodiadoSalvo = custodiadoRepository.save(custodiado);
            log.info("Custodiado salvo no banco - ID gerado: {}", custodiadoSalvo.getId());

            // Criar histórico de endereço inicial
            criarHistoricoEnderecoInicial(custodiadoSalvo, dto);
            log.debug("Histórico de endereço inicial criado");

            // Criar primeiro comparecimento no histórico (CADASTRO_INICIAL)
            criarPrimeiroComparecimento(custodiadoSalvo);
            log.debug("Primeiro comparecimento criado");

            log.info("Custodiado cadastrado com sucesso - ID: {}, Nome: {}, Processo: {}",
                    custodiadoSalvo.getId(), custodiadoSalvo.getNome(), custodiadoSalvo.getProcesso());

            return custodiadoSalvo;

        } catch (Exception e) {
            log.error("Erro ao cadastrar custodiado - Nome: {}, Processo: {}, Erro: {}",
                    dto.getNome(), dto.getProcesso(), e.getMessage(), e);
            throw e; // Re-throw para o GlobalExceptionHandler tratar
        }
    }

    @Transactional
    public Custodiado update(Long id, CustodiadoDTO dto) {
        log.info("Atualizando custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        if (custodiado.isArquivado()) {
            throw new IllegalArgumentException("Não é possível atualizar custodiado arquivado. Reative-o primeiro.");
        }

        // Limpar e formatar dados antes das validações
        dto.limparEFormatarDados();

        // Validações (excluindo duplicidades do próprio registro e considerando apenas ATIVOS)
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentosParaUpdate(dto, id);
        validarDatasLogicas(dto);

        // Atualizar dados básicos
        custodiado.setNome(dto.getNome().trim());
        custodiado.setCpf(dto.getCpf());
        custodiado.setRg(dto.getRg());
        custodiado.setContato(dto.getContato());
        custodiado.setProcesso(dto.getProcesso().trim());
        custodiado.setVara(dto.getVara().trim());
        custodiado.setComarca(dto.getComarca().trim());
        custodiado.setDataDecisao(dto.getDataDecisao());
        custodiado.setPeriodicidade(dto.getPeriodicidade());
        custodiado.setDataComparecimentoInicial(dto.getDataComparecimentoInicial());
        custodiado.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null);

        // Recalcular próximo comparecimento se necessário
        custodiado.calcularProximoComparecimento();

        Custodiado custodiadoAtualizado = custodiadoRepository.save(custodiado);
        log.info("Custodiado atualizado com sucesso - ID: {}, Nome: {}",
                custodiadoAtualizado.getId(), custodiadoAtualizado.getNome());

        return custodiadoAtualizado;
    }

    // ========== MÉTODOS DE BUSCA (APENAS ATIVOS) ==========

    @Transactional(readOnly = true)
    public List<Custodiado> findByStatus(StatusComparecimento status) {
        log.info("Buscando custodiados ATIVOS por status: {}", status);

        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório. Use: EM_CONFORMIDADE ou INADIMPLENTE");
        }

        return custodiadoRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findComparecimentosHoje() {
        log.info("Buscando custodiados ATIVOS com comparecimento hoje");
        return custodiadoRepository.findComparecimentosHoje();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findInadimplentes() {
        log.info("Buscando custodiados ATIVOS inadimplentes");
        return custodiadoRepository.findInadimplentes();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> buscarPorNomeOuProcesso(String termo) {
        log.info("Buscando custodiados ATIVOS por termo: {}", termo);

        if (termo == null || termo.trim().isEmpty()) {
            throw new IllegalArgumentException("Termo de busca é obrigatório");
        }

        String termoLimpo = termo.trim();
        if (termoLimpo.length() < 2) {
            throw new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres");
        }

        return custodiadoRepository.buscarPorNomeOuProcesso(termoLimpo, termoLimpo);
    }

    // ========== NOVOS MÉTODOS PARA CONTROLE DE SITUAÇÃO ==========

    @Transactional(readOnly = true)
    public List<Custodiado> findBySituacao(SituacaoCustodiado situacao) {
        log.info("Buscando custodiados por situação: {}", situacao);
        return custodiadoRepository.findBySituacao(situacao);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllArchived() {
        log.info("Buscando todos os custodiados ARQUIVADOS");
        return custodiadoRepository.findAllArchived();
    }

    @Transactional(readOnly = true)
    public long countBySituacao(SituacaoCustodiado situacao) {
        return custodiadoRepository.countBySituacao(situacao);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findReactivatable() {
        log.info("Buscando custodiados que podem ser reativados");
        return custodiadoRepository.findReactivatable();
    }

    // ========== MÉTODOS DE ENDEREÇO (SEM LAZY LOADING) ==========

    public String getEnderecoCompleto(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);

        return enderecoAtivo.map(HistoricoEndereco::getEnderecoCompleto)
                .orElse("Endereço não informado");
    }

    public String getEnderecoResumido(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);

        return enderecoAtivo.map(HistoricoEndereco::getEnderecoResumido)
                .orElse("Sem endereço");
    }

    public String getCidadeEstado(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);

        if (enderecoAtivo.isPresent()) {
            HistoricoEndereco endereco = enderecoAtivo.get();
            return endereco.getCidade() + " - " + endereco.getEstado();
        }
        return "Não informado";
    }

    // ========== MÉTODOS PRIVADOS ==========

    private void criarHistoricoEnderecoInicial(Custodiado custodiado, CustodiadoDTO dto) {
        try {
            log.debug("Criando histórico de endereço inicial para custodiado ID: {}", custodiado.getId());

            // CORREÇÃO: Desativar quaisquer endereços ativos existentes (segurança)
            int desativados = historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());

            if (desativados > 0) {
                log.warn("Desativados {} endereços ativos pré-existentes para custodiado ID: {}",
                        desativados, custodiado.getId());
            }

            // Verificar se já existe endereço ativo para evitar duplicidade
            Optional<HistoricoEndereco> enderecoExistente =
                    historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiado.getId());

            if (enderecoExistente.isPresent()) {
                log.warn("Custodiado ID: {} já possui endereço ativo, desativando antes de criar novo",
                        custodiado.getId());
                historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());
            }

            HistoricoEndereco enderecoInicial = HistoricoEndereco.builder()
                    .custodiado(custodiado)
                    .cep(formatarCep(dto.getCep().trim()))
                    .logradouro(dto.getLogradouro().trim())
                    .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                    .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                    .bairro(dto.getBairro().trim())
                    .cidade(dto.getCidade().trim())
                    .estado(dto.getEstado().trim().toUpperCase())
                    .dataInicio(custodiado.getDataComparecimentoInicial())
                    .dataFim(null) // Endereço ativo não tem data fim
                    .ativo(Boolean.TRUE)
                    .motivoAlteracao("Endereço inicial no cadastro")
                    .validadoPor("Sistema ACLP")
                    .build();

            HistoricoEndereco enderecoSalvo = historicoEnderecoRepository.save(enderecoInicial);

            // Garantia final: desativar outros endereços ativos (caso algum tenha sido criado concorrentemente)
            historicoEnderecoRepository.desativarOutrosEnderecosAtivos(custodiado.getId(), enderecoSalvo.getId());

            log.info("Histórico de endereço inicial criado - ID: {}, Custodiado: {}, Endereço: {}",
                    enderecoSalvo.getId(), custodiado.getNome(), enderecoSalvo.getEnderecoResumido());

        } catch (Exception e) {
            log.error("Erro ao criar histórico de endereço inicial para custodiado ID: " + custodiado.getId(), e);
            throw new RuntimeException("Erro ao criar endereço inicial: " + e.getMessage(), e);
        }
    }

    private void criarPrimeiroComparecimento(Custodiado custodiado) {
        try {
            log.debug("Criando primeiro comparecimento para custodiado ID: {}", custodiado.getId());

            // Verificar se já existe comparecimento inicial para evitar duplicidade
            boolean jaTemComparecimento = historicoComparecimentoRepository
                    .existsCadastroInicialPorCustodiado(custodiado.getId());

            if (jaTemComparecimento) {
                log.warn("Custodiado ID: {} já possui cadastro inicial, pulando criação", custodiado.getId());
                return;
            }

            HistoricoComparecimento primeiroComparecimento = HistoricoComparecimento.builder()
                    .custodiado(custodiado)
                    .dataComparecimento(custodiado.getDataComparecimentoInicial())
                    .horaComparecimento(LocalTime.now())
                    .tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                    .validadoPor("Sistema ACLP")
                    .observacoes("Cadastro inicial no sistema")
                    .mudancaEndereco(Boolean.FALSE)
                    .build();

            HistoricoComparecimento comparecimentoSalvo = historicoComparecimentoRepository.save(primeiroComparecimento);
            log.info("Primeiro comparecimento registrado - ID: {}, Custodiado: {}, Data: {}",
                    comparecimentoSalvo.getId(), custodiado.getNome(), custodiado.getDataComparecimentoInicial());

        } catch (Exception e) {
            log.error("Erro ao criar primeiro comparecimento para custodiado ID: " + custodiado.getId(), e);
            throw new RuntimeException("Erro ao criar primeiro comparecimento: " + e.getMessage(), e);
        }
    }

    // ========== MÉTODOS DE VALIDAÇÃO ==========

    private void validarDadosObrigatorios(CustodiadoDTO dto) {
        if (dto.getNome() == null || dto.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome é obrigatório");
        }

        if (dto.getContato() == null || dto.getContato().trim().isEmpty()) {
            throw new IllegalArgumentException("Contato é obrigatório");
        }

        if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        if (dto.getVara() == null || dto.getVara().trim().isEmpty()) {
            throw new IllegalArgumentException("Vara é obrigatória");
        }

        if (dto.getComarca() == null || dto.getComarca().trim().isEmpty()) {
            throw new IllegalArgumentException("Comarca é obrigatória");
        }

        if (dto.getDataDecisao() == null) {
            throw new IllegalArgumentException("Data da decisão é obrigatória");
        }

        if (dto.getPeriodicidade() == null || dto.getPeriodicidade() <= 0) {
            throw new IllegalArgumentException("Periodicidade deve ser um número positivo (em dias)");
        }

        // if (dto.getDataComparecimentoInicial() == null) {
        //     throw new IllegalArgumentException("Data do comparecimento inicial é obrigatória");
        // }

        // Pelo menos CPF ou RG deve estar preenchido
        if ((dto.getCpf() == null || dto.getCpf().trim().isEmpty()) &&
                (dto.getRg() == null || dto.getRg().trim().isEmpty())) {
            throw new IllegalArgumentException("Pelo menos um documento (CPF ou RG) deve ser informado");
        }
    }
    private void validarEnderecoCompleto(CustodiadoDTO dto) {
        if (dto.getCep() == null || dto.getCep().trim().isEmpty()) {
            throw new IllegalArgumentException("CEP é obrigatório");
        }

        if (dto.getLogradouro() == null || dto.getLogradouro().trim().isEmpty()) {
            throw new IllegalArgumentException("Logradouro é obrigatório");
        }

        if (dto.getBairro() == null || dto.getBairro().trim().isEmpty()) {
            throw new IllegalArgumentException("Bairro é obrigatório");
        }

        if (dto.getCidade() == null || dto.getCidade().trim().isEmpty()) {
            throw new IllegalArgumentException("Cidade é obrigatória");
        }

        if (dto.getEstado() == null || dto.getEstado().trim().isEmpty()) {
            throw new IllegalArgumentException("Estado é obrigatório");
        }

        if (!dto.hasEnderecoCompleto()) {
            throw new IllegalArgumentException("Endereço completo é obrigatório. Preencha: CEP, logradouro, bairro, cidade e estado");
        }
    }

    private void validarFormatos(CustodiadoDTO dto) {
        // Validar nome
        if (dto.getNome() != null && dto.getNome().trim().length() > 150) {
            throw new IllegalArgumentException("Nome deve ter no máximo 150 caracteres");
        }

        // Validar CPF se fornecido
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = dto.getCpf().trim();
            if (!validarFormatoCpf(cpf)) {
                throw new IllegalArgumentException("CPF deve ter formato válido (xxx.xxx.xxx-xx ou apenas números)");
            }
        }

        //  Validação de processo mais flexível
        if (dto.getProcesso() != null && !validarFormatoProcessoFlexivel(dto.getProcesso().trim())) {
            throw new IllegalArgumentException("Processo deve ter formato válido (números e pontos/hífens)");
        }

        // Validar periodicidade
        if (dto.getPeriodicidade() != null && (dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365)) {
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");
        }

        //  Validação de contato mais flexível
        if (dto.getContato() != null && !validarFormatoContatoFlexivel(dto.getContato().trim())) {
            throw new IllegalArgumentException("Contato deve ter formato válido de telefone");
        }

        // Validar CEP
        if (dto.getCep() != null && !dto.getCep().trim().isEmpty()) {
            if (!validarFormatoCep(dto.getCep().trim())) {
                throw new IllegalArgumentException("CEP deve ter formato válido (00000-000 ou apenas números)");
            }
        }

        // Validar estado usando o enum
        if (dto.getEstado() != null && !dto.getEstado().trim().isEmpty()) {
            try {
                EstadoBrasil.fromString(dto.getEstado().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Estado inválido: " + dto.getEstado() + ". " + e.getMessage());
            }
        }
    }

    private void validarDuplicidadesDocumentos(CustodiadoDTO dto) {
        log.debug("Validando duplicidade de documentos - CPF: {}, RG: {}", dto.getCpf(), dto.getRg());

        // Verificar CPF duplicado entre ATIVOS (se fornecido)
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);

            log.debug("Verificando CPF formatado: {}", cpfFormatado);
            boolean cpfExiste = custodiadoRepository.existsByCpfAndSituacaoAtivo(cpfFormatado);
            log.debug("CPF existe em custodiados ativos: {}", cpfExiste);

            if (cpfExiste) {
                throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo no sistema");
            }
        }

        // Verificar RG duplicado entre ATIVOS (se fornecido)
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            log.debug("Verificando RG: {}", dto.getRg().trim());
            boolean rgExiste = custodiadoRepository.existsByRgAndSituacaoAtivo(dto.getRg().trim());
            log.debug("RG existe em custodiados ativos: {}", rgExiste);

            if (rgExiste) {
                throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo no sistema");
            }
        }

        log.debug("Validação de duplicidade concluída com sucesso");
    }

    private void validarDuplicidadesDocumentosParaUpdate(CustodiadoDTO dto, Long idAtual) {
        log.debug("Validando duplicidade para update - ID: {}, CPF: {}, RG: {}", idAtual, dto.getCpf(), dto.getRg());

        // Verificar CPF duplicado em outro registro ATIVO
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);

            log.debug("Verificando CPF formatado para update: {}", cpfFormatado);
            boolean cpfExiste = custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(cpfFormatado, idAtual);
            log.debug("CPF existe em outros custodiados ativos: {}", cpfExiste);

            if (cpfExiste) {
                throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo no sistema");
            }
        }

        // Verificar RG duplicado em outro registro ATIVO
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            log.debug("Verificando RG para update: {}", dto.getRg().trim());
            boolean rgExiste = custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(dto.getRg().trim(), idAtual);
            log.debug("RG existe em outros custodiados ativos: {}", rgExiste);

            if (rgExiste) {
                throw new IllegalArgumentException("RG já está cadastrado para outro custodiado ativo no sistema");
            }
        }

        log.debug("Validação de duplicidade para update concluída com sucesso");
    }

    private void validarDuplicidadesDocumentosParaReativacao(Custodiado custodiado) {
        log.debug("Validando duplicidade para reativação - ID: {}, CPF: {}, RG: {}",
                custodiado.getId(), custodiado.getCpf(), custodiado.getRg());

        // Verificar CPF duplicado entre ATIVOS
        if (custodiado.getCpf() != null && !custodiado.getCpf().trim().isEmpty()) {
            boolean cpfExiste = custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(
                    custodiado.getCpf(), custodiado.getId());
            log.debug("CPF existe em outros custodiados ativos para reativação: {}", cpfExiste);

            if (cpfExiste) {
                throw new IllegalArgumentException(
                        "Não é possível reativar: CPF já está em uso por outro custodiado ativo");
            }
        }

        // Verificar RG duplicado entre ATIVOS
        if (custodiado.getRg() != null && !custodiado.getRg().trim().isEmpty()) {
            boolean rgExiste = custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(
                    custodiado.getRg(), custodiado.getId());
            log.debug("RG existe em outros custodiados ativos para reativação: {}", rgExiste);

            if (rgExiste) {
                throw new IllegalArgumentException(
                        "Não é possível reativar: RG já está em uso por outro custodiado ativo");
            }
        }

        log.debug("Validação de duplicidade para reativação concluída com sucesso");
    }

    private void validarDatasLogicas(CustodiadoDTO dto) {
        LocalDate hoje = LocalDate.now();

        if (dto.getDataDecisao() != null && dto.getDataDecisao().isAfter(hoje)) {
            throw new IllegalArgumentException("Data da decisão não pode ser uma data futura");
        }

        if (dto.getDataComparecimentoInicial() != null && dto.getDataDecisao() != null) {
            if (dto.getDataComparecimentoInicial().isBefore(dto.getDataDecisao())) {
                throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior à data da decisão");
            }
        }

        //  Validação mais flexível para datas antigas
        if (dto.getDataComparecimentoInicial() != null &&
                dto.getDataComparecimentoInicial().isBefore(hoje.minusYears(5))) {
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior a 5 anos");
        }
    }

    // ========== MÉTODOS UTILITÁRIOS ==========

    private boolean validarFormatoCpf(String cpf) {
        String cpfLimpo = limparCpf(cpf);
        return cpfLimpo.matches("\\d{11}") && !cpfLimpo.matches("(\\d)\\1{10}");
    }

    //  Validação mais flexível para processo
    private boolean validarFormatoProcesso(String processo) {
        return processo.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}");
    }

    private boolean validarFormatoProcessoFlexivel(String processo) {
        // Aceita qualquer combinação de números, pontos e hífens
        return processo.matches("^[\\d.-]+$") && processo.length() >= 10;
    }

    private boolean validarFormatoContato(String contato) {
        return contato.matches("\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}");
    }

    //  Validação mais flexível para contato
    private boolean validarFormatoContatoFlexivel(String contato) {
        // Remove todos os caracteres não numéricos
        String numeros = contato.replaceAll("[^\\d]", "");
        // Aceita telefones com 10 ou 11 dígitos (com ou sem DDD)
        return numeros.length() >= 8 && numeros.length() <= 11;
    }

    private boolean validarFormatoCep(String cep) {
        String cepLimpo = cep.replaceAll("[^\\d]", "");
        return cepLimpo.matches("\\d{8}");
    }

    private String limparCpf(String cpf) {
        return cpf != null ? cpf.replaceAll("[^\\d]", "") : "";
    }

    private String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return cpf;
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." +
                cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private String formatarCep(String cep) {
        if (cep == null) return null;
        String cepLimpo = cep.replaceAll("[^\\d]", "");
        if (cepLimpo.length() == 8) {
            return cepLimpo.substring(0, 5) + "-" + cepLimpo.substring(5);
        }
        return cep;
    }
}