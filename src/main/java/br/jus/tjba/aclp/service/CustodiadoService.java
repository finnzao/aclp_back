package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.StatusCustodiado;
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

    @Transactional(readOnly = true)
    public List<Custodiado> findAll() {
        log.info("Buscando todos os custodiados cadastrados");
        return custodiadoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllAtivos() {
        log.info("Buscando apenas custodiados ATIVOS");
        return custodiadoRepository.findAllAtivos();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findArquivados() {
        log.info("Buscando custodiados ARQUIVADOS");
        return custodiadoRepository.findArquivados();
    }

    @Transactional(readOnly = true)
    public Optional<Custodiado> findById(Long id) {
        log.info("Buscando custodiado por ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        return custodiadoRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcesso(String processo) {
        log.info("Buscando custodiados por processo: {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        return custodiadoRepository.findByProcesso(processo.trim());
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcessoAtivos(String processo) {
        log.info("Buscando custodiados ATIVOS por processo: {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        return custodiadoRepository.findByProcesso(processo.trim()).stream()
                .filter(Custodiado::isAtivo)
                .toList();
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
        validarDuplicidadesDocumentosAtivos(dto);
        validarDatasLogicas(dto);
        validarEnderecoCompleto(dto);

        // Criar custodiado sempre como ATIVO
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
                .statusCustodiado(StatusCustodiado.ATIVO)
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

        log.info("Custodiado cadastrado com sucesso - ID: {}, Nome: {}, Processo: {}, Status: ATIVO",
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

        // Limpar e formatar dados antes das validações
        dto.limparEFormatarDados();

        // Validações (excluindo duplicidades do próprio registro)
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentosAtivosParaUpdate(dto, id);
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

        // Atualizar endereço se fornecido
        if (dto.hasEnderecoCompleto()) {
            atualizarEnderecoAtivo(custodiado, dto);
        }

        // Recalcular próximo comparecimento se necessário
        custodiado.calcularProximoComparecimento();
        custodiado.atualizarStatusBaseadoEmData();

        Custodiado custodiadoAtualizado = custodiadoRepository.save(custodiado);
        log.info("Custodiado atualizado com sucesso - ID: {}, Nome: {}",
                custodiadoAtualizado.getId(), custodiadoAtualizado.getNome());

        return custodiadoAtualizado;
    }

    @Transactional
    public void delete(Long id) {
        log.info("Excluindo custodiado ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        // Verificar se há dependências que impedem a exclusão
        long comparecimentos = historicoComparecimentoRepository.countByCustodiado(custodiado);
        if (comparecimentos > 0) {
            throw new IllegalArgumentException(
                    String.format("Não é possível excluir custodiado que possui %d comparecimento(s) registrado(s)",
                            comparecimentos));
        }

        custodiadoRepository.delete(custodiado);
        log.info("Custodiado excluído com sucesso - ID: {}", id);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByStatus(StatusComparecimento status) {
        log.info("Buscando custodiados por status: {}", status);

        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório. Use: EM_CONFORMIDADE ou INADIMPLENTE");
        }

        return custodiadoRepository.findByStatusAndAtivo(status);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findComparecimentosHoje() {
        log.info("Buscando custodiados com comparecimento hoje");
        return custodiadoRepository.findComparecimentosHoje();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findInadimplentes() {
        log.info("Buscando custodiados inadimplentes");
        return custodiadoRepository.findInadimplentes();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> buscarPorNomeOuProcesso(String termo) {
        log.info("Buscando custodiados por termo: {}", termo);

        if (termo == null || termo.trim().isEmpty()) {
            throw new IllegalArgumentException("Termo de busca é obrigatório");
        }

        String termoLimpo = termo.trim();
        if (termoLimpo.length() < 2) {
            throw new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres");
        }

        return custodiadoRepository.buscarPorNomeOuProcessoAtivos(termoLimpo, termoLimpo);
    }

    /**
     * Arquiva um custodiado (muda status para ARQUIVADO)
     */
    @Transactional
    public Custodiado arquivar(Long id) {
        log.info("Arquivando custodiado ID: {}", id);

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        if (custodiado.isArquivado()) {
            throw new IllegalArgumentException("Custodiado já está arquivado");
        }

        custodiado.arquivar();
        Custodiado arquivado = custodiadoRepository.save(custodiado);

        log.info("Custodiado arquivado com sucesso - ID: {}, Nome: {}", arquivado.getId(), arquivado.getNome());
        return arquivado;
    }

    /**
     * Reativa um custodiado arquivado
     */
    @Transactional
    public Custodiado reativar(Long id) {
        log.info("Reativando custodiado ID: {}", id);

        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));

        if (custodiado.isAtivo()) {
            throw new IllegalArgumentException("Custodiado já está ativo");
        }

        custodiado.reativar();
        Custodiado reativado = custodiadoRepository.save(custodiado);

        log.info("Custodiado reativado com sucesso - ID: {}, Nome: {}", reativado.getId(), reativado.getNome());
        return reativado;
    }

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

    private void atualizarEnderecoAtivo(Custodiado custodiado, CustodiadoDTO dto) {
        // Buscar endereço ativo atual
        Optional<HistoricoEndereco> enderecoAtivoOpt =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiado.getId());

        if (enderecoAtivoOpt.isPresent()) {
            HistoricoEndereco enderecoAtivo = enderecoAtivoOpt.get();

            // Verificar se houve mudança real no endereço
            boolean mudou = !enderecoAtivo.getCep().equals(dto.getCep()) ||
                    !enderecoAtivo.getLogradouro().equals(dto.getLogradouro()) ||
                    !enderecoAtivo.getBairro().equals(dto.getBairro()) ||
                    !enderecoAtivo.getCidade().equals(dto.getCidade()) ||
                    !enderecoAtivo.getEstado().equals(dto.getEstado());

            if (mudou) {
                // Finalizar endereço anterior
                enderecoAtivo.finalizarEndereco(LocalDate.now());
                historicoEnderecoRepository.save(enderecoAtivo);

                // Criar novo endereço
                HistoricoEndereco novoEndereco = HistoricoEndereco.builder()
                        .custodiado(custodiado)
                        .cep(dto.getCep().trim())
                        .logradouro(dto.getLogradouro().trim())
                        .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                        .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                        .bairro(dto.getBairro().trim())
                        .cidade(dto.getCidade().trim())
                        .estado(dto.getEstado().trim().toUpperCase())
                        .dataInicio(LocalDate.now())
                        .ativo(Boolean.TRUE)
                        .motivoAlteracao("Atualização cadastral")
                        .validadoPor("Sistema ACLP")
                        .build();

                historicoEnderecoRepository.save(novoEndereco);

                log.info("Endereço atualizado - Custodiado: {}, Novo endereço: {}",
                        custodiado.getNome(), novoEndereco.getEnderecoResumido());
            }
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

    private void validarDuplicidadesDocumentosAtivos(CustodiadoDTO dto) {
        // Verificar CPF duplicado em custodiados ATIVOS
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);

            if (custodiadoRepository.existsByCpfAndAtivoAndIdNot(cpfFormatado, null)) {
                throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo no sistema");
            }
        }

        // Verificar RG duplicado em custodiados ATIVOS
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndAtivoAndIdNot(dto.getRg().trim(), null)) {
                throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo no sistema");
            }
        }
    }

    private void validarDuplicidadesDocumentosAtivosParaUpdate(CustodiadoDTO dto, Long idAtual) {
        // Verificar CPF duplicado em outro custodiado ATIVO
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);

            if (custodiadoRepository.existsByCpfAndAtivoAndIdNot(cpfFormatado, idAtual)) {
                throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo no sistema");
            }
        }

        // Verificar RG duplicado em outro custodiado ATIVO
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndAtivoAndIdNot(dto.getRg().trim(), idAtual)) {
                throw new IllegalArgumentException("RG já está cadastrado para outro custodiado ativo no sistema");
            }
        }
    }

    private void validarDuplicidadesDocumentos(CustodiadoDTO dto) {
        // Mantido para compatibilidade - redireciona para o novo método
        validarDuplicidadesDocumentosAtivos(dto);
    }

    private void validarDuplicidadesDocumentosParaUpdate(CustodiadoDTO dto, Long idAtual) {
        // Mantido para compatibilidade - redireciona para o novo método
        validarDuplicidadesDocumentosAtivosParaUpdate(dto, idAtual);
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
        return cpfLimpo.matches("\\d{11}") && !cpfLimpo.matches("(\\d)\\1{10}");
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