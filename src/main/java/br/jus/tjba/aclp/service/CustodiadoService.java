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


    /**
     * Busca todos os custodiados ATIVOS com endereços carregados (SEM N+1)
     * USA: findAllWithEnderecosAtivos() que faz JOIN FETCH
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findAll() {
        log.info("Buscando todos os custodiados ATIVOS (otimizado com JOIN FETCH)");
        return custodiadoRepository.findAllWithEnderecosAtivos();
    }

    /**
     * Busca todos incluindo arquivados com endereços carregados (SEM N+1)
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findAllIncludingArchived() {
        log.info("Buscando todos os custodiados (ATIVOS + ARQUIVADOS) - otimizado");
        return custodiadoRepository.findAllIncludingArchivedWithEnderecos();
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

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcesso(String processo) {
        log.info("Buscando custodiados ATIVOS por processo: {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        String processoFormatado = formatarProcesso(processo.trim());
        return custodiadoRepository.findByProcesso(processoFormatado);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcessoIncludingArchived(String processo) {
        log.info("Buscando todos os custodiados por processo (incluindo arquivados): {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        String processoFormatado = formatarProcesso(processo.trim());
        return custodiadoRepository.findByProcessoIncludingArchived(processoFormatado);
    }

    @Transactional
    public Custodiado save(CustodiadoDTO dto) {
        log.info("Iniciando cadastro de novo custodiado - Processo: {}, Nome: {}",
                dto.getProcesso(), dto.getNome());

        try {
            dto.setId(null);
            dto.limparEFormatarDados();

            if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
                dto.setCpf(formatarCpf(dto.getCpf().trim()));
            }
            if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty()) {
                dto.setProcesso(formatarProcesso(dto.getProcesso().trim()));
            }

            log.debug("Dados limpos e formatados - CPF: {}, Processo: {}", dto.getCpf(), dto.getProcesso());

            if (dto.getDataComparecimentoInicial() == null) {
                dto.setDataComparecimentoInicial(LocalDate.now());
                log.info("Data de comparecimento inicial não fornecida - usando data atual: {}",
                        LocalDate.now());
            }

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
                    .processo(dto.getProcesso())
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

            criarHistoricoEnderecoInicial(custodiadoSalvo, dto);
            log.debug("Histórico de endereço inicial criado");

            criarPrimeiroComparecimento(custodiadoSalvo);
            log.debug("Primeiro comparecimento criado");

            log.info("Custodiado cadastrado com sucesso - ID: {}, Nome: {}, Processo: {}",
                    custodiadoSalvo.getId(), custodiadoSalvo.getNome(), custodiadoSalvo.getProcesso());

            return custodiadoSalvo;

        } catch (Exception e) {
            log.error("Erro ao cadastrar custodiado - Nome: {}, Processo: {}, Erro: {}",
                    dto.getNome(), dto.getProcesso(), e.getMessage(), e);
            throw e;
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

        dto.limparEFormatarDados();

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            dto.setCpf(formatarCpf(dto.getCpf().trim()));
        }
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty()) {
            dto.setProcesso(formatarProcesso(dto.getProcesso().trim()));
        }

        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentosParaUpdate(dto, id);
        validarDatasLogicas(dto);

        custodiado.setNome(dto.getNome().trim());
        custodiado.setCpf(dto.getCpf());
        custodiado.setRg(dto.getRg());
        custodiado.setContato(dto.getContato());
        custodiado.setProcesso(dto.getProcesso());
        custodiado.setVara(dto.getVara().trim());
        custodiado.setComarca(dto.getComarca().trim());
        custodiado.setDataDecisao(dto.getDataDecisao());
        custodiado.setPeriodicidade(dto.getPeriodicidade());
        custodiado.setDataComparecimentoInicial(dto.getDataComparecimentoInicial());
        custodiado.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null);

        custodiado.calcularProximoComparecimento();

        Custodiado custodiadoAtualizado = custodiadoRepository.save(custodiado);
        log.info("Custodiado atualizado com sucesso - ID: {}, Nome: {}",
                custodiadoAtualizado.getId(), custodiadoAtualizado.getNome());

        return custodiadoAtualizado;
    }

    // ==========  MÉTODOS DE BUSCA OTIMIZADOS ==========

    /**
     * Busca por status com endereços carregados (SEM N+1)
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findByStatus(StatusComparecimento status) {
        log.info("Buscando custodiados ATIVOS por status: {} (otimizado)", status);

        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório. Use: EM_CONFORMIDADE ou INADIMPLENTE");
        }

        return custodiadoRepository.findByStatusWithEnderecos(status);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findComparecimentosHoje() {
        log.info("Buscando custodiados ATIVOS com comparecimento hoje");
        return custodiadoRepository.findComparecimentosHoje();
    }

    /**
     * Busca inadimplentes com endereços carregados (SEM N+1)
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findInadimplentes() {
        log.info("Buscando custodiados ATIVOS inadimplentes (otimizado)");
        return custodiadoRepository.findInadimplentesWithEnderecos();
    }

    /**
     * Busca por nome ou processo com endereços carregados (SEM N+1)
     */
    @Transactional(readOnly = true)
    public List<Custodiado> buscarPorNomeOuProcesso(String termo) {
        log.info("Buscando custodiados ATIVOS por termo: {} (otimizado)", termo);

        if (termo == null || termo.trim().isEmpty()) {
            throw new IllegalArgumentException("Termo de busca é obrigatório");
        }

        String termoLimpo = termo.trim();
        if (termoLimpo.length() < 2) {
            throw new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres");
        }

        String termoProcesso = termoLimpo;
        if (termoLimpo.replaceAll("[^\\d]", "").length() >= 10) {
            try {
                termoProcesso = formatarProcesso(termoLimpo);
            } catch (Exception e) {
                termoProcesso = termoLimpo;
            }
        }

        return custodiadoRepository.buscarPorNomeOuProcessoWithEnderecos(termoLimpo, termoProcesso);
    }

    // ========== MÉTODOS PARA CONTROLE DE SITUAÇÃO ==========

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

    // ========== MÉTODOS PRIVADOS (mantidos sem alterações) ==========

    private void criarHistoricoEnderecoInicial(Custodiado custodiado, CustodiadoDTO dto) {
        try {
            log.debug("Criando histórico de endereço inicial para custodiado ID: {}", custodiado.getId());

            int desativados = historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());

            if (desativados > 0) {
                log.warn("Desativados {} endereços ativos pré-existentes para custodiado ID: {}",
                        desativados, custodiado.getId());
            }

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
                    .dataFim(null)
                    .ativo(Boolean.TRUE)
                    .motivoAlteracao("Endereço inicial no cadastro")
                    .validadoPor("Sistema ACLP")
                    .build();

            HistoricoEndereco enderecoSalvo = historicoEnderecoRepository.save(enderecoInicial);

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

    // ========== MÉTODOS DE VALIDAÇÃO (mantidos sem alterações) ==========

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
        if (dto.getNome() != null && dto.getNome().trim().length() > 150) {
            throw new IllegalArgumentException("Nome deve ter no máximo 150 caracteres");
        }

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = dto.getCpf().trim();
            if (!validarCpfAlgoritmo(cpf)) {
                throw new IllegalArgumentException("CPF inválido. Verifique os dígitos verificadores");
            }
        }

        if (dto.getProcesso() != null && !validarFormatoProcesso(dto.getProcesso().trim())) {
            throw new IllegalArgumentException("Processo deve ter formato válido (0000000-00.0000.0.00.0000)");
        }

        if (dto.getPeriodicidade() != null && (dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365)) {
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");
        }

        if (dto.getContato() != null && !validarFormatoContatoFlexivel(dto.getContato().trim())) {
            throw new IllegalArgumentException("Contato deve ter formato válido de telefone");
        }

        if (dto.getCep() != null && !dto.getCep().trim().isEmpty()) {
            if (!validarFormatoCep(dto.getCep().trim())) {
                throw new IllegalArgumentException("CEP deve ter formato válido (00000-000 ou apenas números)");
            }
        }

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

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpfFormatado = dto.getCpf();

            log.debug("Verificando CPF formatado: {}", cpfFormatado);
            boolean cpfExiste = custodiadoRepository.existsByCpfAndSituacaoAtivo(cpfFormatado);
            log.debug("CPF existe em custodiados ativos: {}", cpfExiste);

            if (cpfExiste) {
                throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo no sistema");
            }
        }

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

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpfFormatado = dto.getCpf();

            log.debug("Verificando CPF formatado para update: {}", cpfFormatado);
            boolean cpfExiste = custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(cpfFormatado, idAtual);
            log.debug("CPF existe em outros custodiados ativos: {}", cpfExiste);

            if (cpfExiste) {
                throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo no sistema");
            }
        }

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

        if (custodiado.getCpf() != null && !custodiado.getCpf().trim().isEmpty()) {
            boolean cpfExiste = custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(
                    custodiado.getCpf(), custodiado.getId());
            log.debug("CPF existe em outros custodiados ativos para reativação: {}", cpfExiste);

            if (cpfExiste) {
                throw new IllegalArgumentException(
                        "Não é possível reativar: CPF já está em uso por outro custodiado ativo");
            }
        }

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

        if (dto.getDataComparecimentoInicial() != null &&
                dto.getDataComparecimentoInicial().isBefore(hoje.minusYears(5))) {
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior a 5 anos");
        }
    }

    // ========== MÉTODOS UTILITÁRIOS ==========

    private boolean validarCpfAlgoritmo(String cpf) {
        String cpfLimpo = limparCpf(cpf);

        if (cpfLimpo.length() != 11) {
            return false;
        }

        if (cpfLimpo.matches("(\\d)\\1{10}")) {
            return false;
        }

        try {
            int[] digitos = new int[11];
            for (int i = 0; i < 11; i++) {
                digitos[i] = Character.getNumericValue(cpfLimpo.charAt(i));
            }

            int soma1 = 0;
            for (int i = 0; i < 9; i++) {
                soma1 += digitos[i] * (10 - i);
            }
            int resto1 = soma1 % 11;
            int dv1 = (resto1 < 2) ? 0 : (11 - resto1);

            if (digitos[9] != dv1) {
                log.debug("CPF inválido - primeiro dígito verificador incorreto. Esperado: {}, Encontrado: {}", dv1, digitos[9]);
                return false;
            }

            int soma2 = 0;
            for (int i = 0; i < 10; i++) {
                soma2 += digitos[i] * (11 - i);
            }
            int resto2 = soma2 % 11;
            int dv2 = (resto2 < 2) ? 0 : (11 - resto2);

            if (digitos[10] != dv2) {
                log.debug("CPF inválido - segundo dígito verificador incorreto. Esperado: {}, Encontrado: {}", dv2, digitos[10]);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Erro ao validar CPF: {}", e.getMessage());
            return false;
        }
    }

    private boolean validarFormatoProcesso(String processo) {
        return processo.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}");
    }

    private String formatarProcesso(String processo) {
        String numeros = processo.replaceAll("[^\\d]", "");

        if (numeros.length() != 20) {
            log.debug("Processo com número de dígitos incorreto: {} (esperado 20)", numeros.length());
            return processo.trim();
        }

        try {
            return String.format("%s-%s.%s.%s.%s.%s",
                    numeros.substring(0, 7),
                    numeros.substring(7, 9),
                    numeros.substring(9, 13),
                    numeros.substring(13, 14),
                    numeros.substring(14, 16),
                    numeros.substring(16, 20)
            );
        } catch (Exception e) {
            log.warn("Não foi possível formatar processo: {}", processo);
            return processo.trim();
        }
    }


    /**
     * Busca todos os custodiados ATIVOS SEM carregar relacionamentos
     * Usa query simples sem relacionamentos para máxima performance
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findAllActive() {
        return custodiadoRepository.findAllActive();
    }

    private boolean validarFormatoContatoFlexivel(String contato) {
        String numeros = contato.replaceAll("[^\\d]", "");
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
        String cpfLimpo = limparCpf(cpf);

        if (cpfLimpo.length() != 11) {
            return cpf.trim();
        }

        if (!validarCpfAlgoritmo(cpfLimpo)) {
            throw new IllegalArgumentException("CPF inválido. Verifique os dígitos verificadores");
        }

        return cpfLimpo.substring(0, 3) + "." +
                cpfLimpo.substring(3, 6) + "." +
                cpfLimpo.substring(6, 9) + "-" +
                cpfLimpo.substring(9);
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