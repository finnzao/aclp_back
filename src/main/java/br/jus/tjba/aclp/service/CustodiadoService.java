package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CadastroInicialDTO;
import br.jus.tjba.aclp.dto.CadastroInicialResponseDTO;
import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.dto.CustodiadoListDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;

import jakarta.persistence.EntityNotFoundException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustodiadoService {

    private final CustodiadoRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final ProcessoRepository processoRepository;

    // =====================================================================
    // CORREÇÃO DE PERFORMANCE: Novos métodos para busca paginada
    // =====================================================================

    /**
     * CORREÇÃO DE PERFORMANCE: Busca paginada de custodiados com filtros.
     *
     * Utiliza Specification do Spring Data JPA para construir queries
     * dinâmicas baseadas nos filtros recebidos, evitando carregar
     * todos os registros em memória.
     *
     * @param page      Número da página (0-indexed)
     * @param size      Quantidade de registros por página
     * @param nome      Filtro opcional por nome (LIKE %nome%)
     * @param cpf       Filtro opcional por CPF
     * @param status    Filtro opcional por status (EM_CONFORMIDADE / INADIMPLENTE)
     * @param ordenarPor Campo para ordenação (nome, proximoComparecimento, status, etc.)
     * @param direcao   Direção da ordenação (asc ou desc)
     */
    @Transactional(readOnly = true)
    public Page<CustodiadoListDTO> listarPaginado(
            int page, int size, String nome, String cpf,
            String status, String ordenarPor, String direcao) {

        // Validar campo de ordenação para evitar injeção SQL
        String campoOrdenacao = validarCampoOrdenacao(ordenarPor);

        Sort sort = direcao.equalsIgnoreCase("desc")
                ? Sort.by(campoOrdenacao).descending()
                : Sort.by(campoOrdenacao).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        // Construir Specification dinâmica com os filtros
        Specification<Custodiado> spec = buildCustodiadoSpecification(nome, cpf, status);

        Page<Custodiado> pagina = custodiadoRepository.findAll(spec, pageable);

        return pagina.map(CustodiadoListDTO::fromEntity);
    }

    /**
     * CORREÇÃO DE PERFORMANCE: Listagem para exportação com filtros.
     *
     * Retorna todos os registros filtrados (sem paginação) para download
     * de planilhas. Chamado apenas no momento do clique em "Exportar".
     */
    @Transactional(readOnly = true)
    public List<CustodiadoListDTO> listarParaExportacao(
            String nome, String cpf, String status,
            String ordenarPor, String direcao) {

        String campoOrdenacao = validarCampoOrdenacao(ordenarPor);

        Sort sort = direcao.equalsIgnoreCase("desc")
                ? Sort.by(campoOrdenacao).descending()
                : Sort.by(campoOrdenacao).ascending();

        Specification<Custodiado> spec = buildCustodiadoSpecification(nome, cpf, status);

        List<Custodiado> custodiados = custodiadoRepository.findAll(spec, sort);
        return custodiados.stream()
                .map(CustodiadoListDTO::fromEntity)
                .toList();
    }

    /**
     * Constrói Specification dinâmica para filtros de custodiado.
     * Todos os filtros são aplicados na query SQL, não em Java.
     */
    private Specification<Custodiado> buildCustodiadoSpecification(
            String nome, String cpf, String status) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filtro base: apenas custodiados ativos
            predicates.add(cb.equal(root.get("situacao"), SituacaoCustodiado.ATIVO));

            // Busca parcial case-insensitive por nome
            if (nome != null && !nome.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("nome")),
                        "%" + nome.toLowerCase().trim() + "%"
                ));
            }

            // Busca por CPF (remove formatação antes de comparar)
            if (cpf != null && !cpf.isBlank()) {
                String cpfLimpo = cpf.replaceAll("\\D", "");
                predicates.add(cb.or(
                        cb.like(root.get("cpf"), "%" + cpfLimpo + "%"),
                        cb.like(root.get("cpf"), "%" + cpf.trim() + "%")
                ));
            }

            // Filtro por status de comparecimento
            if (status != null && !status.isBlank()) {
                try {
                    StatusComparecimento statusEnum = StatusComparecimento.fromString(status);
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException e) {
                    log.warn("Status de filtro inválido ignorado: {}", status);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Valida o campo de ordenação para evitar injeção SQL.
     * Retorna um campo seguro ou "nome" como fallback.
     */
    private String validarCampoOrdenacao(String campo) {
        if (campo == null) return "nome";

        return switch (campo.toLowerCase().trim()) {
            case "nome" -> "nome";
            case "cpf" -> "cpf";
            case "status" -> "status";
            case "proximocomparecimento", "proximo_comparecimento" -> "proximoComparecimento";
            case "ultimocomparecimento", "ultimo_comparecimento" -> "ultimoComparecimento";
            case "comarca" -> "comarca";
            case "vara" -> "vara";
            case "periodicidade" -> "periodicidade";
            case "criadoem", "criado_em" -> "criadoEm";
            default -> {
                log.warn("Campo de ordenação desconhecido '{}', usando 'nome' como fallback", campo);
                yield "nome";
            }
        };
    }

    // =====================================================================
    // CADASTRO INICIAL — cria tudo em uma única transação (inalterado)
    // =====================================================================

    @Transactional
    public CadastroInicialResponseDTO cadastroInicial(CadastroInicialDTO dto) {
        log.info("=== CADASTRO INICIAL === Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        dto.limparEFormatarDados();
        validarCadastroInicial(dto);

        String cpfFormatado = (dto.getCpf() != null && !dto.getCpf().trim().isEmpty())
                ? formatarCpf(dto.getCpf().trim()) : null;
        String processoFormatado = formatarProcesso(dto.getProcesso().trim());
        String contatoFinal = dto.getContatoEfetivo();
        LocalDate dataCompInicial = dto.getDataComparecimentoInicial() != null
                ? dto.getDataComparecimentoInicial() : LocalDate.now();

        validarDuplicidadesDocumentosCadastroInicial(cpfFormatado, dto.getRg());

        Custodiado custodiado = Custodiado.builder()
                .nome(dto.getNome().trim())
                .cpf(cpfFormatado)
                .rg(dto.getRg())
                .contato(contatoFinal)
                .processo(processoFormatado)
                .vara(dto.getVara().trim())
                .comarca(dto.getComarca().trim())
                .dataDecisao(dto.getDataDecisao())
                .periodicidade(dto.getPeriodicidade())
                .dataComparecimentoInicial(dataCompInicial)
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .situacao(SituacaoCustodiado.ATIVO)
                .ultimoComparecimento(dataCompInicial)
                .observacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null)
                .build();

        custodiado.calcularProximoComparecimento();
        Custodiado custodiadoSalvo = custodiadoRepository.save(custodiado);
        log.info("Custodiado criado — publicId: {}, ID: {}", custodiadoSalvo.getPublicId(), custodiadoSalvo.getId());

        Processo processo = Processo.builder()
                .custodiado(custodiadoSalvo)
                .numeroProcesso(processoFormatado)
                .vara(dto.getVara().trim())
                .comarca(dto.getComarca().trim())
                .dataDecisao(dto.getDataDecisao())
                .periodicidade(dto.getPeriodicidade())
                .dataComparecimentoInicial(dataCompInicial)
                .ultimoComparecimento(dataCompInicial)
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .situacaoProcesso(SituacaoProcesso.ATIVO)
                .observacoes(dto.getObservacoes())
                .build();

        processo.calcularProximoComparecimento();
        Processo processoSalvo = processoRepository.save(processo);
        log.info("Processo criado — ID: {}, Número: {}", processoSalvo.getId(), processoSalvo.getNumeroProcesso());

        String cepFormatado = formatarCep(dto.getCep().trim());
        HistoricoEndereco endereco = HistoricoEndereco.builder()
                .custodiado(custodiadoSalvo)
                .cep(cepFormatado)
                .logradouro(dto.getLogradouro().trim())
                .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                .bairro(dto.getBairro().trim())
                .cidade(dto.getCidade().trim())
                .estado(dto.getEstado().trim().toUpperCase())
                .dataInicio(dataCompInicial)
                .ativo(Boolean.TRUE)
                .motivoAlteracao("Endereço inicial no cadastro")
                .validadoPor("Sistema SCC")
                .build();

        HistoricoEndereco enderecoSalvo = historicoEnderecoRepository.save(endereco);

        HistoricoComparecimento comparecimento = HistoricoComparecimento.builder()
                .custodiado(custodiadoSalvo)
                .processo(processoSalvo)
                .dataComparecimento(dataCompInicial)
                .horaComparecimento(LocalTime.now())
                .tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                .validadoPor("Sistema SCC")
                .observacoes("Cadastro inicial no sistema")
                .mudancaEndereco(Boolean.FALSE)
                .build();

        historicoComparecimentoRepository.save(comparecimento);

        log.info("=== CADASTRO INICIAL CONCLUÍDO === publicId: {}, Processo: {}",
                custodiadoSalvo.getPublicId(), processoSalvo.getNumeroProcesso());

        return CadastroInicialResponseDTO.builder()
                .custodiadoId(custodiadoSalvo.getPublicId().toString())
                .nome(custodiadoSalvo.getNome())
                .cpf(custodiadoSalvo.getCpf())
                .rg(custodiadoSalvo.getRg())
                .contato(custodiadoSalvo.getContato())
                .contatoPendente(custodiadoSalvo.isContatoPendente())
                .identificacao(custodiadoSalvo.getIdentificacao())
                .processoId(processoSalvo.getId())
                .numeroProcesso(processoSalvo.getNumeroProcesso())
                .vara(processoSalvo.getVara())
                .comarca(processoSalvo.getComarca())
                .dataDecisao(processoSalvo.getDataDecisao())
                .periodicidade(processoSalvo.getPeriodicidade())
                .periodicidadeDescricao(processoSalvo.getPeriodicidadeDescricao())
                .dataComparecimentoInicial(processoSalvo.getDataComparecimentoInicial())
                .statusProcesso(processoSalvo.getStatus())
                .proximoComparecimento(processoSalvo.getProximoComparecimento())
                .enderecoCompleto(enderecoSalvo.getEnderecoCompleto())
                .enderecoResumido(enderecoSalvo.getEnderecoResumido())
                .cidade(enderecoSalvo.getCidade())
                .estado(enderecoSalvo.getEstado())
                .observacoes(custodiadoSalvo.getObservacoes())
                .criadoEm(custodiadoSalvo.getCriadoEm())
                .build();
    }

    // =====================================================================
    // Validações (inalteradas)
    // =====================================================================

    private void validarCadastroInicial(CadastroInicialDTO dto) {
        if (dto.getNome() == null || dto.getNome().trim().isEmpty())
            throw new IllegalArgumentException("Nome é obrigatório");
        if (dto.getNome().trim().length() > 150)
            throw new IllegalArgumentException("Nome deve ter no máximo 150 caracteres");

        boolean temCpf = dto.getCpf() != null && !dto.getCpf().trim().isEmpty();
        boolean temRg = dto.getRg() != null && !dto.getRg().trim().isEmpty();
        if (!temCpf && !temRg)
            throw new IllegalArgumentException("Pelo menos um documento (CPF ou RG) deve ser informado");

        if (temCpf && !validarCpfAlgoritmo(dto.getCpf().trim()))
            throw new IllegalArgumentException("CPF inválido. Verifique os dígitos verificadores");

        if (dto.getContato() != null && !dto.getContato().trim().isEmpty()) {
            if (!validarFormatoContatoFlexivel(dto.getContato().trim()))
                throw new IllegalArgumentException("Contato deve ter formato válido de telefone");
        }

        if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        String processoFormatado = formatarProcesso(dto.getProcesso().trim());
        if (!validarFormatoProcesso(processoFormatado))
            throw new IllegalArgumentException("Processo deve ter formato válido (0000000-00.0000.0.00.0000)");

        if (dto.getVara() == null || dto.getVara().trim().isEmpty())
            throw new IllegalArgumentException("Vara é obrigatória");
        if (dto.getComarca() == null || dto.getComarca().trim().isEmpty())
            throw new IllegalArgumentException("Comarca é obrigatória");

        if (dto.getDataDecisao() == null)
            throw new IllegalArgumentException("Data da decisão é obrigatória");
        if (dto.getDataDecisao().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Data da decisão não pode ser futura");
        if (dto.getDataComparecimentoInicial() != null && dto.getDataComparecimentoInicial().isBefore(dto.getDataDecisao()))
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior à data da decisão");

        if (dto.getPeriodicidade() == null || dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365)
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");

        if (dto.getCep() == null || dto.getCep().trim().isEmpty())
            throw new IllegalArgumentException("CEP é obrigatório");
        if (!validarFormatoCep(dto.getCep().trim()))
            throw new IllegalArgumentException("CEP deve ter formato válido");
        if (dto.getLogradouro() == null || dto.getLogradouro().trim().isEmpty())
            throw new IllegalArgumentException("Logradouro é obrigatório");
        if (dto.getBairro() == null || dto.getBairro().trim().isEmpty())
            throw new IllegalArgumentException("Bairro é obrigatório");
        if (dto.getCidade() == null || dto.getCidade().trim().isEmpty())
            throw new IllegalArgumentException("Cidade é obrigatória");
        if (dto.getEstado() == null || dto.getEstado().trim().isEmpty())
            throw new IllegalArgumentException("Estado é obrigatório");
        try {
            EstadoBrasil.fromString(dto.getEstado().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido: " + dto.getEstado());
        }
    }

    private void validarDuplicidadesDocumentosCadastroInicial(String cpf, String rg) {
        if (cpf != null && !cpf.trim().isEmpty()) {
            if (custodiadoRepository.existsByCpfAndSituacaoAtivo(cpf))
                throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo no sistema");
        }
        if (rg != null && !rg.trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivo(rg.trim()))
                throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo no sistema");
        }
    }

    // =====================================================================
    // Métodos existentes (inalterados)
    // =====================================================================

    @Transactional(readOnly = true)
    public Optional<Custodiado> findByPublicId(String publicIdStr) {
        if (publicIdStr == null || publicIdStr.trim().isEmpty())
            throw new IllegalArgumentException("publicId não pode ser vazio");
        try {
            UUID publicId = UUID.fromString(publicIdStr);
            return custodiadoRepository.findByPublicId(publicId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Custodiado updateByPublicId(String publicIdStr, CustodiadoDTO dto) {
        UUID publicId = parseUuid(publicIdStr);
        Custodiado custodiado = custodiadoRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado: " + publicIdStr));
        return atualizarCustodiado(custodiado, dto);
    }

    @Transactional
    public void deleteByPublicId(String publicIdStr) {
        UUID publicId = parseUuid(publicIdStr);
        Custodiado custodiado = custodiadoRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado: " + publicIdStr));
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Custodiado já está arquivado");
        custodiado.arquivar();
        custodiadoRepository.save(custodiado);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAll() { return custodiadoRepository.findAllWithEnderecosAtivos(); }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllIncludingArchived() { return custodiadoRepository.findAllIncludingArchivedWithEnderecos(); }

    @Transactional(readOnly = true)
    public Optional<Custodiado> findById(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        return custodiadoRepository.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));
        if (custodiado.isArquivado()) throw new IllegalArgumentException("Custodiado já está arquivado");
        custodiado.arquivar();
        custodiadoRepository.save(custodiado);
    }

    @Transactional
    public Custodiado reativar(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));
        if (!custodiado.isArquivado()) throw new IllegalArgumentException("Custodiado já está ativo");
        custodiado.reativar();
        return custodiadoRepository.save(custodiado);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcesso(String processo) {
        if (processo == null || processo.trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        return custodiadoRepository.findByProcesso(formatarProcesso(processo.trim()));
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByProcessoIncludingArchived(String processo) {
        if (processo == null || processo.trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        return custodiadoRepository.findByProcessoIncludingArchived(formatarProcesso(processo.trim()));
    }

    @Transactional
    public Custodiado save(CustodiadoDTO dto) {
        log.info("Cadastrando custodiado (endpoint legado) — Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        dto.setId(null);
        dto.limparEFormatarDados();

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty())
            dto.setCpf(formatarCpf(dto.getCpf().trim()));
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty())
            dto.setProcesso(formatarProcesso(dto.getProcesso().trim()));
        if (dto.getDataComparecimentoInicial() == null)
            dto.setDataComparecimentoInicial(LocalDate.now());

        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentos(dto);
        validarDatasLogicas(dto);
        validarEnderecoCompleto(dto);

        String contatoFinal = dto.getContatoEfetivo();

        Custodiado custodiado = Custodiado.builder()
                .nome(dto.getNome().trim())
                .cpf(dto.getCpf())
                .rg(dto.getRg())
                .contato(contatoFinal)
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
        Custodiado salvo = custodiadoRepository.save(custodiado);
        criarHistoricoEnderecoInicial(salvo, dto);
        criarPrimeiroComparecimento(salvo);
        return salvo;
    }

    @Transactional
    public Custodiado update(Long id, CustodiadoDTO dto) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));
        return atualizarCustodiado(custodiado, dto);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByStatus(StatusComparecimento status) {
        if (status == null) throw new IllegalArgumentException("Status é obrigatório");
        return custodiadoRepository.findByStatusWithEnderecos(status);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findComparecimentosHoje() { return custodiadoRepository.findComparecimentosHoje(); }

    @Transactional(readOnly = true)
    public List<Custodiado> findInadimplentes() { return custodiadoRepository.findInadimplentesWithEnderecos(); }

    @Transactional(readOnly = true)
    public List<Custodiado> buscarPorNomeOuProcesso(String termo) {
        if (termo == null || termo.trim().isEmpty()) throw new IllegalArgumentException("Termo de busca é obrigatório");
        if (termo.trim().length() < 2) throw new IllegalArgumentException("Termo deve ter pelo menos 2 caracteres");
        String t = termo.trim();
        String tp = t;
        if (t.replaceAll("[^\\d]", "").length() >= 10) { try { tp = formatarProcesso(t); } catch (Exception ignored) {} }
        return custodiadoRepository.buscarPorNomeOuProcessoWithEnderecos(t, tp);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findBySituacao(SituacaoCustodiado situacao) { return custodiadoRepository.findBySituacao(situacao); }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllArchived() { return custodiadoRepository.findAllArchived(); }

    @Transactional(readOnly = true)
    public long countBySituacao(SituacaoCustodiado situacao) { return custodiadoRepository.countBySituacao(situacao); }

    @Transactional(readOnly = true)
    public List<Custodiado> findReactivatable() { return custodiadoRepository.findReactivatable(); }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllActive() { return custodiadoRepository.findAllActive(); }

    public String getEnderecoCompleto(Long id) {
        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(id).map(HistoricoEndereco::getEnderecoCompleto).orElse("Endereço não informado");
    }

    public String getEnderecoResumido(Long id) {
        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(id).map(HistoricoEndereco::getEnderecoResumido).orElse("Sem endereço");
    }

    public String getCidadeEstado(Long id) {
        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(id).map(e -> e.getCidade() + " - " + e.getEstado()).orElse("Não informado");
    }

    // =====================================================================
    // Métodos privados (inalterados)
    // =====================================================================

    private Custodiado atualizarCustodiado(Custodiado custodiado, CustodiadoDTO dto) {
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Não é possível atualizar custodiado arquivado. Reative-o primeiro.");
        dto.limparEFormatarDados();
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) dto.setCpf(formatarCpf(dto.getCpf().trim()));
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty()) dto.setProcesso(formatarProcesso(dto.getProcesso().trim()));
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentosParaUpdate(dto, custodiado.getId());
        validarDatasLogicas(dto);

        custodiado.setNome(dto.getNome().trim());
        custodiado.setCpf(dto.getCpf());
        custodiado.setRg(dto.getRg());
        custodiado.setContato(dto.getContatoEfetivo());
        custodiado.setProcesso(dto.getProcesso());
        custodiado.setVara(dto.getVara().trim());
        custodiado.setComarca(dto.getComarca().trim());
        custodiado.setDataDecisao(dto.getDataDecisao());
        custodiado.setPeriodicidade(dto.getPeriodicidade());
        custodiado.setDataComparecimentoInicial(dto.getDataComparecimentoInicial());
        custodiado.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null);
        custodiado.calcularProximoComparecimento();
        return custodiadoRepository.save(custodiado);
    }

    private void criarHistoricoEnderecoInicial(Custodiado custodiado, CustodiadoDTO dto) {
        historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());
        HistoricoEndereco e = HistoricoEndereco.builder()
                .custodiado(custodiado).cep(formatarCep(dto.getCep().trim())).logradouro(dto.getLogradouro().trim())
                .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                .bairro(dto.getBairro().trim()).cidade(dto.getCidade().trim()).estado(dto.getEstado().trim().toUpperCase())
                .dataInicio(custodiado.getDataComparecimentoInicial()).ativo(Boolean.TRUE)
                .motivoAlteracao("Endereço inicial no cadastro").validadoPor("Sistema SCC").build();
        HistoricoEndereco salvo = historicoEnderecoRepository.save(e);
        historicoEnderecoRepository.desativarOutrosEnderecosAtivos(custodiado.getId(), salvo.getId());
    }

    private void criarPrimeiroComparecimento(Custodiado custodiado) {
        if (historicoComparecimentoRepository.existsCadastroInicialPorCustodiado(custodiado.getId())) return;
        HistoricoComparecimento c = HistoricoComparecimento.builder()
                .custodiado(custodiado).dataComparecimento(custodiado.getDataComparecimentoInicial())
                .horaComparecimento(LocalTime.now()).tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                .validadoPor("Sistema SCC").observacoes("Cadastro inicial no sistema").mudancaEndereco(Boolean.FALSE).build();
        historicoComparecimentoRepository.save(c);
    }

    private void validarDadosObrigatorios(CustodiadoDTO dto) {
        if (dto.getNome() == null || dto.getNome().trim().isEmpty()) throw new IllegalArgumentException("Nome é obrigatório");
        if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty()) throw new IllegalArgumentException("Número do processo é obrigatório");
        if (dto.getVara() == null || dto.getVara().trim().isEmpty()) throw new IllegalArgumentException("Vara é obrigatória");
        if (dto.getComarca() == null || dto.getComarca().trim().isEmpty()) throw new IllegalArgumentException("Comarca é obrigatória");
        if (dto.getDataDecisao() == null) throw new IllegalArgumentException("Data da decisão é obrigatória");
        if (dto.getPeriodicidade() == null || dto.getPeriodicidade() <= 0) throw new IllegalArgumentException("Periodicidade deve ser um número positivo");
        boolean temCpf = dto.getCpf() != null && !dto.getCpf().trim().isEmpty();
        boolean temRg = dto.getRg() != null && !dto.getRg().trim().isEmpty();
        if (!temCpf && !temRg) throw new IllegalArgumentException("Pelo menos um documento (CPF ou RG) deve ser informado");
    }

    private void validarEnderecoCompleto(CustodiadoDTO dto) {
        if (dto.getCep() == null || dto.getCep().trim().isEmpty()) throw new IllegalArgumentException("CEP é obrigatório");
        if (dto.getLogradouro() == null || dto.getLogradouro().trim().isEmpty()) throw new IllegalArgumentException("Logradouro é obrigatório");
        if (dto.getBairro() == null || dto.getBairro().trim().isEmpty()) throw new IllegalArgumentException("Bairro é obrigatório");
        if (dto.getCidade() == null || dto.getCidade().trim().isEmpty()) throw new IllegalArgumentException("Cidade é obrigatória");
        if (dto.getEstado() == null || dto.getEstado().trim().isEmpty()) throw new IllegalArgumentException("Estado é obrigatório");
    }

    private void validarFormatos(CustodiadoDTO dto) {
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty() && !validarCpfAlgoritmo(dto.getCpf().trim()))
            throw new IllegalArgumentException("CPF inválido");
        if (dto.getProcesso() != null && !validarFormatoProcesso(dto.getProcesso().trim()))
            throw new IllegalArgumentException("Processo deve ter formato válido");
        if (dto.getPeriodicidade() != null && (dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365))
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");
        if (dto.getContato() != null && !dto.getContato().trim().isEmpty() && !"Pendente".equalsIgnoreCase(dto.getContato().trim()))
            if (!validarFormatoContatoFlexivel(dto.getContato().trim()))
                throw new IllegalArgumentException("Contato deve ter formato válido de telefone");
        if (dto.getCep() != null && !dto.getCep().trim().isEmpty() && !validarFormatoCep(dto.getCep().trim()))
            throw new IllegalArgumentException("CEP inválido");
        if (dto.getEstado() != null && !dto.getEstado().trim().isEmpty()) {
            try { EstadoBrasil.fromString(dto.getEstado().trim()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Estado inválido: " + dto.getEstado()); }
        }
    }

    private void validarDuplicidadesDocumentos(CustodiadoDTO dto) {
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty() && custodiadoRepository.existsByCpfAndSituacaoAtivo(dto.getCpf()))
            throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo");
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty() && custodiadoRepository.existsByRgAndSituacaoAtivo(dto.getRg().trim()))
            throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo");
    }

    private void validarDuplicidadesDocumentosParaUpdate(CustodiadoDTO dto, Long idAtual) {
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty() && custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(dto.getCpf(), idAtual))
            throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo");
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty() && custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(dto.getRg().trim(), idAtual))
            throw new IllegalArgumentException("RG já está cadastrado para outro custodiado ativo");
    }

    private void validarDatasLogicas(CustodiadoDTO dto) {
        LocalDate hoje = LocalDate.now();
        if (dto.getDataDecisao() != null && dto.getDataDecisao().isAfter(hoje))
            throw new IllegalArgumentException("Data da decisão não pode ser futura");
        if (dto.getDataComparecimentoInicial() != null && dto.getDataDecisao() != null && dto.getDataComparecimentoInicial().isBefore(dto.getDataDecisao()))
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior à data da decisão");
    }

    private boolean validarCpfAlgoritmo(String cpf) {
        String c = cpf.replaceAll("[^\\d]", "");
        if (c.length() != 11 || c.matches("(\\d)\\1{10}")) return false;
        try {
            int[] d = new int[11]; for (int i = 0; i < 11; i++) d[i] = Character.getNumericValue(c.charAt(i));
            int s1 = 0; for (int i = 0; i < 9; i++) s1 += d[i] * (10 - i); int r1 = s1 % 11; int dv1 = r1 < 2 ? 0 : 11 - r1;
            if (d[9] != dv1) return false;
            int s2 = 0; for (int i = 0; i < 10; i++) s2 += d[i] * (11 - i); int r2 = s2 % 11; int dv2 = r2 < 2 ? 0 : 11 - r2;
            return d[10] == dv2;
        } catch (Exception e) { return false; }
    }

    private boolean validarFormatoProcesso(String p) { return p.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}"); }

    private String formatarProcesso(String p) {
        String n = p.replaceAll("[^\\d]", "");
        if (n.length() != 20) return p.trim();
        return String.format("%s-%s.%s.%s.%s.%s", n.substring(0,7), n.substring(7,9), n.substring(9,13), n.substring(13,14), n.substring(14,16), n.substring(16,20));
    }

    private boolean validarFormatoContatoFlexivel(String c) { String n = c.replaceAll("[^\\d]", ""); return n.length() >= 8 && n.length() <= 11; }
    private boolean validarFormatoCep(String c) { return c.replaceAll("[^\\d]", "").matches("\\d{8}"); }

    private String formatarCpf(String cpf) {
        String c = cpf.replaceAll("[^\\d]", "");
        if (c.length() != 11) return cpf.trim();
        if (!validarCpfAlgoritmo(c)) throw new IllegalArgumentException("CPF inválido");
        return c.substring(0,3) + "." + c.substring(3,6) + "." + c.substring(6,9) + "-" + c.substring(9);
    }

    private String formatarCep(String cep) {
        if (cep == null) return null;
        String c = cep.replaceAll("[^\\d]", "");
        return c.length() == 8 ? c.substring(0,5) + "-" + c.substring(5) : cep;
    }

    private UUID parseUuid(String v) {
        try { return UUID.fromString(v); } catch (IllegalArgumentException e) { throw new EntityNotFoundException("Identificador inválido: " + v); }
    }
}
