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

    /**
     * Lista APENAS custodiados ATIVOS (comportamento do endpoint GET /api/custodiados)
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findAll() {
        log.info("Buscando todos os custodiados ATIVOS");
        return custodiadoRepository.findAll(); // Repository já retorna apenas ATIVOS
    }

    /**
     * Lista TODOS os custodiados (ATIVOS + ARQUIVADOS) para novo endpoint
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findAllIncludingArchived() {
        log.info("Buscando todos os custodiados (ATIVOS + ARQUIVADOS)");
        return custodiadoRepository.findAllIncludingArchived();
    }

    /**
     * Busca por ID - funciona para ATIVOS e ARQUIVADOS
     */
    @Transactional(readOnly = true)
    public Optional<Custodiado> findById(Long id) {
        log.info("Buscando custodiado por ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        // Busca em todos (ATIVOS + ARQUIVADOS) para permitir visualização de arquivados
        return custodiadoRepository.findById(id);
    }

    /**
     * DELETE - ARQUIVA o custodiado (não exclui fisicamente)
     */
    @Transactional
    public void delete(Long id) {
        log.info("Arquivando custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        // Verificar se já está arquivado
        if (custodiado.isArquivado()) {
            throw new IllegalArgumentException("Custodiado já está arquivado");
        }

        // Arquivar (método da entidade)
        custodiado.arquivar();
        custodiadoRepository.save(custodiado);

        log.info("Custodiado arquivado com sucesso - ID: {}, Nome: {}", id, custodiado.getNome());
    }

    /**
     * NOVO MÉTODO - Reativa custodiado arquivado
     */
    @Transactional
    public Custodiado reativar(Long id) {
        log.info("Reativando custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        // Verificar se está arquivado
        if (!custodiado.isArquivado()) {
            throw new IllegalArgumentException("Custodiado já está ativo");
        }

        // Verificar duplicidade de documentos antes de reativar
        validarDuplicidadesDocumentosParaReativacao(custodiado);

        // Reativar (método da entidade)
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

    /**
     * Busca por processo incluindo ARQUIVADOS (para relatórios completos)
     */
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

        // Limpar e formatar dados antes das validações
        dto.limparEFormatarDados();

        // Validações
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentos(dto); // Agora valida apenas entre ATIVOS
        validarDatasLogicas(dto);
        validarEnderecoCompleto(dto);

        // Criar custodiado SEMPRE com situacao = ATIVO
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
                .situacao(SituacaoCustodiado.ATIVO) // ✅ SEMPRE ATIVO ao criar
                .ultimoComparecimento(dto.getDataComparecimentoInicial())
                .observacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null)
                .build();

        // Calcular próximo comparecimento
        custodiado.calcularProximoComparecimento();

        // Salvar custodiado
        Custodiado custodiadoSalvo = custodiadoRepository.save(custodiado);

        // Criar histórico de endereço inicial
        criarHistoricoEnderecoInicial(custodiadoSalvo, dto);

        // Criar primeiro comparecimento no histórico (CADASTRO_INICIAL)
        criarPrimeiroComparecimento(custodiadoSalvo);

        log.info("Custodiado cadastrado com sucesso - ID: {}, Nome: {}, Processo: {}",
                custodiadoSalvo.getId(), custodiadoSalvo.getNome(), custodiadoSalvo.getProcesso());

        return custodiadoSalvo;
    }

    @Transactional
    public Custodiado update(Long id, CustodiadoDTO dto) {
        log.info("Atualizando custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        // Verificar se está arquivado
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

    /**
     * Lista custodiados por situação
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findBySituacao(SituacaoCustodiado situacao) {
        log.info("Buscando custodiados por situação: {}", situacao);
        return custodiadoRepository.findBySituacao(situacao);
    }

    /**
     * Lista apenas custodiados ARQUIVADOS
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findAllArchived() {
        log.info("Buscando todos os custodiados ARQUIVADOS");
        return custodiadoRepository.findAllArchived();
    }

    /**
     * Conta custodiados por situação
     */
    @Transactional(readOnly = true)
    public long countBySituacao(SituacaoCustodiado situacao) {
        return custodiadoRepository.countBySituacao(situacao);
    }

    /**
     * Busca custodiados que podem ser reativados
     */
    @Transactional(readOnly = true)
    public List<Custodiado> findReactivatable() {
        log.info("Buscando custodiados que podem ser reativados");
        return custodiadoRepository.findReactivatable();
    }

    // ========== MÉTODOS DE ENDEREÇO (SEM LAZY LOADING) ==========

    /**
     * Busca endereço atual de um custodiado usando repository específico
     * Evita lazy loading dos relacionamentos
     */
    public String getEnderecoCompleto(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);

        return enderecoAtivo.map(HistoricoEndereco::getEnderecoCompleto)
                .orElse("Endereço não informado");
    }

    /**
     * Busca endereço resumido de um custodiado usando repository específico
     */
    public String getEnderecoResumido(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);

        return enderecoAtivo.map(HistoricoEndereco::getEnderecoResumido)
                .orElse("Sem endereço");
    }

    /**
     * Busca cidade e estado de um custodiado
     */
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

    /**
     * Método para criar histórico de endereço inicial
     */
    private void criarHistoricoEnderecoInicial(Custodiado custodiado, CustodiadoDTO dto) {
        HistoricoEndereco enderecoInicial = HistoricoEndereco.builder()
                .custodiado(custodiado)
                .cep(dto.getCep().trim())
                .logradouro(dto.getLogradouro().trim())
                .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                .bairro(dto.getBairro().trim())
                .cidade(dto.getCidade().trim())
                .estado(dto.getEstado().trim().toUpperCase())
                .dataInicio(custodiado.getDataComparecimentoInicial())
                .ativo(Boolean.TRUE)
                .motivoAlteracao("Endereço inicial no cadastro")
                .validadoPor("Sistema ACLP")
                .build();

        historicoEnderecoRepository.save(enderecoInicial);
        custodiado.adicionarHistoricoEndereco(enderecoInicial);

        log.info("Histórico de endereço inicial criado - Custodiado: {}, Endereço: {}",
                custodiado.getNome(), enderecoInicial.getEnderecoResumido());
    }

    /**
     * Método para criar primeiro comparecimento (CADASTRO_INICIAL)
     */
    private void criarPrimeiroComparecimento(Custodiado custodiado) {
        HistoricoComparecimento primeiroComparecimento = HistoricoComparecimento.builder()
                .custodiado(custodiado)
                .dataComparecimento(custodiado.getDataComparecimentoInicial())
                .horaComparecimento(LocalTime.now())
                .tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                .validadoPor("Sistema ACLP")
                .observacoes("Cadastro inicial no sistema")
                .mudancaEndereco(Boolean.FALSE)
                .build();

        historicoComparecimentoRepository.save(primeiroComparecimento);
        custodiado.adicionarHistorico(primeiroComparecimento);

        log.info("Primeiro comparecimento registrado - Custodiado: {}, Data: {}",
                custodiado.getNome(), custodiado.getDataComparecimentoInicial());
    }

    // ========== MÉTODOS DE VALIDAÇÃO (ATUALIZADOS) ==========

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

        if (dto.getDataComparecimentoInicial() == null) {
            throw new IllegalArgumentException("Data do comparecimento inicial é obrigatória");
        }

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

        // Validar formato do processo
        if (dto.getProcesso() != null && !validarFormatoProcesso(dto.getProcesso().trim())) {
            throw new IllegalArgumentException("Processo deve ter formato válido (xxxxxxx-xx.xxxx.x.xx.xxxx)");
        }

        // Validar periodicidade
        if (dto.getPeriodicidade() != null && (dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365)) {
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");
        }

        // Validar contato
        if (dto.getContato() != null && !validarFormatoContato(dto.getContato().trim())) {
            throw new IllegalArgumentException("Contato deve ter formato válido de telefone (ex: (11) 99999-9999)");
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

    /**
     * ✅ ATUALIZADO - Validar duplicidade apenas entre custodiados ATIVOS
     */
    private void validarDuplicidadesDocumentos(CustodiadoDTO dto) {
        // Verificar CPF duplicado entre ATIVOS (se fornecido)
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);

            if (custodiadoRepository.existsByCpfAndSituacaoAtivo(cpfFormatado)) {
                throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo no sistema");
            }
        }

        // Verificar RG duplicado entre ATIVOS (se fornecido)
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivo(dto.getRg().trim())) {
                throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo no sistema");
            }
        }

        // NÃO validar duplicidade de processo - permitir múltiplos custodiados no mesmo processo
    }

    /**
     * ✅ ATUALIZADO - Validar duplicidade para update apenas entre custodiados ATIVOS
     */
    private void validarDuplicidadesDocumentosParaUpdate(CustodiadoDTO dto, Long idAtual) {
        // Verificar CPF duplicado em outro registro ATIVO
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);

            if (custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(cpfFormatado, idAtual)) {
                throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo no sistema");
            }
        }

        // Verificar RG duplicado em outro registro ATIVO
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(dto.getRg().trim(), idAtual)) {
                throw new IllegalArgumentException("RG já está cadastrado para outro custodiado ativo no sistema");
            }
        }
    }

    /**
     * ✅ NOVO - Validar duplicidade antes de reativar custodiado
     */
    private void validarDuplicidadesDocumentosParaReativacao(Custodiado custodiado) {
        // Verificar CPF duplicado entre ATIVOS
        if (custodiado.getCpf() != null && !custodiado.getCpf().trim().isEmpty()) {
            if (custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(
                    custodiado.getCpf(), custodiado.getId())) {
                throw new IllegalArgumentException(
                        "Não é possível reativar: CPF já está em uso por outro custodiado ativo");
            }
        }

        // Verificar RG duplicado entre ATIVOS
        if (custodiado.getRg() != null && !custodiado.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(
                    custodiado.getRg(), custodiado.getId())) {
                throw new IllegalArgumentException(
                        "Não é possível reativar: RG já está em uso por outro custodiado ativo");
            }
        }
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

        // Validar se a data do comparecimento inicial não é muito antiga (por exemplo, mais de 2 anos)
        if (dto.getDataComparecimentoInicial() != null &&
                dto.getDataComparecimentoInicial().isBefore(hoje.minusYears(2))) {
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior a 2 anos");
        }
    }

    // ========== MÉTODOS UTILITÁRIOS ==========

    private boolean validarFormatoCpf(String cpf) {
        String cpfLimpo = limparCpf(cpf);
        return cpfLimpo.matches("\\d{11}") && !cpfLimpo.matches("(\\d)\\1{10}"); // Não aceita CPF com todos os dígitos iguais
    }

    private boolean validarFormatoProcesso(String processo) {
        return processo.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}");
    }

    private boolean validarFormatoContato(String contato) {
        return contato.matches("\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}");
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
}