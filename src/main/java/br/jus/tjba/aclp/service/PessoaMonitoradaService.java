package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CadastroInicialDTO;
import br.jus.tjba.aclp.dto.CadastroInicialResponseDTO;
import br.jus.tjba.aclp.dto.PessoaMonitoradaDTO;
import br.jus.tjba.aclp.dto.PessoaMonitoradaListDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
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
public class PessoaMonitoradaService {

    private final PessoaMonitoradaRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final ProcessoRepository processoRepository;
    private final ExecucaoPenalService execucaoPenalService;
    private final jakarta.persistence.EntityManager entityManager;

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
    // =====================================================================
    // LISTAGEM: uma SQL por pagina (LEFT JOIN do processo mais urgente).
    // Substitui o par Specification+entidade e o N+1 da natureza.
    // ponytail: subquery com ROW_NUMBER e portavel (H2 dos testes + PG);
    // trocar por LEFT JOIN LATERAL se processos passar de dezenas de milhares.
    // =====================================================================

    private static final String LISTA_SELECT = """
            SELECT c.id, c.public_id, c.nome, c.cpf, c.rg, c.situacao,
                   pp.numero_processo, pp.vara, pp.comarca, pp.periodicidade,
                   pp.ultimo_comparecimento, pp.proximo_comparecimento,
                   CASE WHEN EXISTS (SELECT 1 FROM processos e WHERE e.custodiado_id = c.id
                                     AND e.situacao_processo = 'ATIVO'
                                     AND e.natureza_vinculo = 'EXECUCAO_REGIME_ABERTO')
                        THEN CASE WHEN EXISTS (SELECT 1 FROM processos e2 WHERE e2.custodiado_id = c.id
                                               AND e2.situacao_processo = 'ATIVO'
                                               AND e2.natureza_vinculo = 'CAUTELAR')
                                  THEN 'MISTO' ELSE 'EXECUCAO' END
                        ELSE 'CAUTELAR' END AS natureza
            """;

    private static final String LISTA_FROM = """
             FROM pessoas_monitoradas c
             LEFT JOIN (
                 SELECT pr.*, ROW_NUMBER() OVER (
                     PARTITION BY pr.custodiado_id
                     ORDER BY pr.proximo_comparecimento ASC NULLS LAST) rn
                 FROM processos pr
                 WHERE pr.situacao_processo = 'ATIVO'
             ) pp ON pp.custodiado_id = c.id AND pp.rn = 1
            """;

    private String montarWhereLista(String nome, String cpf, String status, String natureza,
                                    java.util.Map<String, Object> params) {
        StringBuilder w = new StringBuilder(" WHERE c.situacao = 'ATIVO'");
        if (nome != null && !nome.isBlank()) {
            w.append(" AND LOWER(c.nome) LIKE :nome");
            params.put("nome", "%" + nome.toLowerCase().trim() + "%");
        }
        if (cpf != null && !cpf.isBlank()) {
            w.append(" AND (c.cpf LIKE :cpfLimpo OR c.cpf LIKE :cpf)");
            params.put("cpfLimpo", "%" + cpf.replaceAll("\\D", "") + "%");
            params.put("cpf", "%" + cpf.trim() + "%");
        }
        if ("INADIMPLENTE".equalsIgnoreCase(status)) {
            w.append(" AND pp.proximo_comparecimento < CURRENT_DATE");
        } else if ("EM_CONFORMIDADE".equalsIgnoreCase(status)) {
            w.append(" AND (pp.proximo_comparecimento IS NULL OR pp.proximo_comparecimento >= CURRENT_DATE)");
        }
        String existsExec = "EXISTS (SELECT 1 FROM processos e WHERE e.custodiado_id = c.id "
                + "AND e.situacao_processo = 'ATIVO' AND e.natureza_vinculo = 'EXECUCAO_REGIME_ABERTO')";
        if ("EXECUCAO".equalsIgnoreCase(natureza)) w.append(" AND ").append(existsExec);
        else if ("CAUTELAR".equalsIgnoreCase(natureza)) w.append(" AND NOT ").append(existsExec);
        return w.toString();
    }

    private String ordenacaoLista(String campo, String direcao) {
        String col = switch (campo == null ? "nome" : campo.toLowerCase().trim()) {
            case "cpf" -> "c.cpf";
            case "status", "proximocomparecimento", "proximo_comparecimento" -> "pp.proximo_comparecimento";
            case "ultimocomparecimento", "ultimo_comparecimento" -> "pp.ultimo_comparecimento";
            case "comarca" -> "pp.comarca";
            case "vara" -> "pp.vara";
            case "periodicidade" -> "pp.periodicidade";
            case "criadoem", "criado_em" -> "c.criado_em";
            default -> "c.nome";
        };
        String dir = "desc".equalsIgnoreCase(direcao) ? "DESC" : "ASC";
        return " ORDER BY " + col + " " + dir + " NULLS LAST, c.nome ASC";
    }

    @SuppressWarnings("unchecked")
    private List<PessoaMonitoradaListDTO> consultarLista(String nome, String cpf, String status,
                                                         String natureza, String ordenarPor,
                                                         String direcao, Integer page, Integer size) {
        var params = new java.util.HashMap<String, Object>();
        String sql = LISTA_SELECT + LISTA_FROM + montarWhereLista(nome, cpf, status, natureza, params)
                + ordenacaoLista(ordenarPor, direcao);
        var q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        if (page != null && size != null) {
            q.setFirstResult(page * size);
            q.setMaxResults(size);
        }
        List<Object[]> linhas = q.getResultList();
        return linhas.stream().map(this::mapearLinhaLista).toList();
    }

    private long contarLista(String nome, String cpf, String status, String natureza) {
        var params = new java.util.HashMap<String, Object>();
        String sql = "SELECT COUNT(*)" + LISTA_FROM + montarWhereLista(nome, cpf, status, natureza, params);
        var q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return ((Number) q.getSingleResult()).longValue();
    }

    private static LocalDate dataDe(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        return LocalDate.parse(v.toString());
    }

    private PessoaMonitoradaListDTO mapearLinhaLista(Object[] r) {
        LocalDate hoje = LocalDate.now();
        LocalDate proximo = dataDe(r[11]);
        Integer periodicidade = r[9] != null ? ((Number) r[9]).intValue() : null;
        long diasAtraso = proximo != null && proximo.isBefore(hoje)
                ? java.time.temporal.ChronoUnit.DAYS.between(proximo, hoje) : 0;
        String cpf = (String) r[3];
        String rg = (String) r[4];
        String documento = cpf != null && !cpf.isBlank() ? cpf
                : rg != null && !rg.isBlank() ? rg : "Sem documento";
        return PessoaMonitoradaListDTO.builder()
                .id(r[1] != null ? r[1].toString() : null)
                .numericId(((Number) r[0]).longValue())
                .nome((String) r[2])
                .cpf(cpf).rg(rg)
                .documentoExibicao(documento)
                .processo((String) r[6]).vara((String) r[7]).comarca((String) r[8])
                .status(diasAtraso > 0 ? StatusComparecimento.INADIMPLENTE : StatusComparecimento.EM_CONFORMIDADE)
                .situacao(SituacaoPessoaMonitorada.valueOf((String) r[5]))
                .ultimoComparecimento(dataDe(r[10]))
                .proximoComparecimento(proximo)
                .diasAtraso(diasAtraso)
                .periodicidade(periodicidade)
                .inadimplente(diasAtraso > 0)
                .comparecimentoHoje(hoje.equals(proximo))
                .urgente(periodicidade != null && diasAtraso >= periodicidade)
                .natureza((String) r[12])
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PessoaMonitoradaListDTO> listarPaginado(
            int page, int size, String nome, String cpf,
            String status, String natureza, String ordenarPor, String direcao) {
        List<PessoaMonitoradaListDTO> conteudo =
                consultarLista(nome, cpf, status, natureza, ordenarPor, direcao, page, size);
        long total = contarLista(nome, cpf, status, natureza);
        return new org.springframework.data.domain.PageImpl<>(conteudo, PageRequest.of(page, size), total);
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> listarParaExportacao(
            String nome, String cpf, String status, String natureza,
            String ordenarPor, String direcao) {
        return consultarLista(nome, cpf, status, natureza, ordenarPor, direcao, null, null);
    }

    /** Mapeia dentro da transacao: o DTO le processos (LAZY) para derivar os dados. */
    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> listarTodosAtivosDTO() {
        return findAllActive().stream().map(PessoaMonitoradaListDTO::fromEntity).toList();
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

        PessoaMonitorada custodiado = PessoaMonitorada.builder()
                .nome(dto.getNome().trim())
                .cpf(cpfFormatado)
                .rg(dto.getRg())
                .contato(contatoFinal)
                .situacao(SituacaoPessoaMonitorada.ATIVO)
                .observacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null)
                .build();

        PessoaMonitorada custodiadoSalvo = custodiadoRepository.save(custodiado);
        log.info("Pessoa monitorada criada — publicId: {}, ID: {}", custodiadoSalvo.getPublicId(), custodiadoSalvo.getId());

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
                .naturezaVinculo(dto.getNaturezaVinculo() != null
                        ? dto.getNaturezaVinculo() : NaturezaVinculo.CAUTELAR)
                .observacoes(dto.getObservacoes())
                .build();

        processo.calcularProximoComparecimento();
        Processo processoSalvo = processoRepository.save(processo);
        log.info("Processo criado — ID: {}, Número: {}", processoSalvo.getId(), processoSalvo.getNumeroProcesso());

        // execucao sem pena nao existe: tudo na mesma transacao do cadastro
        if (processoSalvo.isExecucao()) {
            if (dto.getPena() == null)
                throw new IllegalArgumentException(
                        "Cadastro de execução de pena exige o bloco 'pena' com os dados da sentença.");
            dto.getPena().setProcessoId(processoSalvo.getId());
            execucaoPenalService.registrarPena(dto.getPena());
        }

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
    public Optional<PessoaMonitorada> findByPublicId(String publicIdStr) {
        if (publicIdStr == null || publicIdStr.trim().isEmpty())
            throw new IllegalArgumentException("publicId não pode ser vazio");
        try {
            UUID publicId = UUID.fromString(publicIdStr);
            // processos junto: os campos derivados da ficha funcionam fora da sessao
            return custodiadoRepository.findByPublicIdComProcessos(publicId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public PessoaMonitorada updateByPublicId(String publicIdStr, PessoaMonitoradaDTO dto) {
        UUID publicId = parseUuid(publicIdStr);
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        return atualizarPessoaMonitorada(custodiado, dto);
    }

    @Transactional
    public void deleteByPublicId(String publicIdStr, br.jus.tjba.aclp.model.enums.MotivoArquivamento motivo) {
        UUID publicId = parseUuid(publicIdStr);
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Pessoa monitorada já está arquivada");
        custodiado.arquivar(motivo);
        custodiadoRepository.save(custodiado);
    }

    @Transactional
    public PessoaMonitorada declararForagido(String publicIdStr, String motivo, String declaradoPor) {
        if (motivo == null || motivo.trim().isEmpty())
            throw new IllegalArgumentException("Motivo é obrigatório para declarar foragido");
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(parseUuid(publicIdStr))
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        if (custodiado.getSituacao() == SituacaoPessoaMonitorada.FORAGIDO)
            throw new IllegalArgumentException("Pessoa monitorada já está declarada foragida");

        custodiado.declararForagido();
        custodiadoRepository.save(custodiado);
        log.warn("FORAGIDO declarado — custodiado {} por {}: {}", custodiado.getNome(), declaradoPor, motivo);

        // evasao em execucao de pena entra no rito de justificacao (LEP 118 p2)
        for (Processo p : processoRepository.findProcessosAtivosByCustodiado(custodiado.getId())) {
            if (p.isExecucao()) {
                execucaoPenalService.registrarIncidenteEvasao(p.getId(), motivo, declaradoPor);
            }
        }
        return custodiado;
    }

    @Transactional
    public PessoaMonitorada reativarPorPublicId(String publicIdStr) {
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(parseUuid(publicIdStr))
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        if (custodiado.getSituacao() == SituacaoPessoaMonitorada.ATIVO)
            throw new IllegalArgumentException("Pessoa monitorada já está ativa");
        custodiado.reativar();
        return custodiadoRepository.save(custodiado);
    }

    @Transactional(readOnly = true)
    public boolean existeArquivadoComCpf(String cpf) {
        return custodiadoRepository.findByCpfAndSituacao(cpf, SituacaoPessoaMonitorada.ARQUIVADO).isPresent();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAll() { return custodiadoRepository.findAllWithEnderecosAtivos(); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAllIncludingArchived() { return custodiadoRepository.findAllIncludingArchivedWithEnderecos(); }

    @Transactional(readOnly = true)
    public Optional<PessoaMonitorada> findById(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        return custodiadoRepository.findByIdComProcessos(id);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        PessoaMonitorada custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada com ID: " + id));
        if (custodiado.isArquivado()) throw new IllegalArgumentException("Pessoa monitorada já está arquivada");
        custodiado.arquivar();
        custodiadoRepository.save(custodiado);
    }

    @Transactional
    public PessoaMonitorada reativar(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        PessoaMonitorada custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada com ID: " + id));
        if (!custodiado.isArquivado()) throw new IllegalArgumentException("Pessoa monitorada já está ativa");
        custodiado.reativar();
        return custodiadoRepository.save(custodiado);
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findByProcesso(String processo) {
        if (processo == null || processo.trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        return custodiadoRepository.findByProcesso(formatarProcesso(processo.trim()));
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findByProcessoIncludingArchived(String processo) {
        if (processo == null || processo.trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        return custodiadoRepository.findByProcessoIncludingArchived(formatarProcesso(processo.trim()));
    }

    @Transactional
    public PessoaMonitorada save(PessoaMonitoradaDTO dto) {
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

        PessoaMonitorada custodiado = PessoaMonitorada.builder()
                .nome(dto.getNome().trim())
                .cpf(dto.getCpf())
                .rg(dto.getRg())
                .contato(contatoFinal)
                .situacao(SituacaoPessoaMonitorada.ATIVO)
                .observacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null)
                .build();

        PessoaMonitorada salvo = custodiadoRepository.save(custodiado);

        // o vinculo mora em PROCESSOS; o endpoint legado cria um CAUTELAR
        Processo processoLegado = Processo.builder()
                .custodiado(salvo)
                .numeroProcesso(dto.getProcesso())
                .vara(dto.getVara().trim())
                .comarca(dto.getComarca().trim())
                .dataDecisao(dto.getDataDecisao())
                .periodicidade(dto.getPeriodicidade())
                .dataComparecimentoInicial(dto.getDataComparecimentoInicial())
                .ultimoComparecimento(dto.getDataComparecimentoInicial())
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .situacaoProcesso(SituacaoProcesso.ATIVO)
                .naturezaVinculo(NaturezaVinculo.CAUTELAR)
                .build();
        processoLegado.calcularProximoComparecimento();
        processoRepository.save(processoLegado);
        salvo.getProcessos().add(processoLegado);
        criarHistoricoEnderecoInicial(salvo, dto);
        criarPrimeiroComparecimento(salvo, processoLegado);
        return salvo;
    }

    @Transactional
    public PessoaMonitorada update(Long id, PessoaMonitoradaDTO dto) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID deve ser um número positivo");
        PessoaMonitorada custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada com ID: " + id));
        return atualizarPessoaMonitorada(custodiado, dto);
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> findByStatus(StatusComparecimento status) {
        if (status == null) throw new IllegalArgumentException("Status é obrigatório");
        var lista = status == StatusComparecimento.INADIMPLENTE
                ? custodiadoRepository.findInadimplentesWithEnderecos()
                : custodiadoRepository.findEmConformidadeAtivos();
        return lista.stream().map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> findComparecimentosHoje() {
        return custodiadoRepository.findComparecimentosHoje().stream()
                .map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> findInadimplentes() {
        return custodiadoRepository.findInadimplentesWithEnderecos().stream()
                .map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> buscarPorNomeOuProcesso(String termo) {
        if (termo == null || termo.trim().isEmpty()) throw new IllegalArgumentException("Termo de busca é obrigatório");
        if (termo.trim().length() < 2) throw new IllegalArgumentException("Termo deve ter pelo menos 2 caracteres");
        String t = termo.trim();
        String tp = t;
        if (t.replaceAll("[^\\d]", "").length() >= 10) { try { tp = formatarProcesso(t); } catch (Exception ignored) {} }
        return custodiadoRepository.buscarPorNomeOuProcessoWithEnderecos(t, tp).stream()
                .map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findBySituacao(SituacaoPessoaMonitorada situacao) { return custodiadoRepository.findBySituacao(situacao); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAllArchived() { return custodiadoRepository.findAllArchived(); }

    @Transactional(readOnly = true)
    public long countBySituacao(SituacaoPessoaMonitorada situacao) { return custodiadoRepository.countBySituacao(situacao); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findReactivatable() { return custodiadoRepository.findReactivatable(); }

    @Transactional(readOnly = true)
    public List<PessoaMonitorada> findAllActive() { return custodiadoRepository.findAllActive(); }

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

    private PessoaMonitorada atualizarPessoaMonitorada(PessoaMonitorada custodiado, PessoaMonitoradaDTO dto) {
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Não é possível atualizar custodiado arquivado. Reative-o primeiro.");
        dto.limparEFormatarDados();
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) dto.setCpf(formatarCpf(dto.getCpf().trim()));
        validarDadosPessoais(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentosParaUpdate(dto, custodiado.getId());

        // dados processuais editam-se no proprio processo (EditarProcessoModal)
        custodiado.setNome(dto.getNome().trim());
        custodiado.setCpf(dto.getCpf());
        custodiado.setRg(dto.getRg());
        custodiado.setContato(dto.getContatoEfetivo());
        custodiado.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null);
        return custodiadoRepository.save(custodiado);
    }

    private void criarHistoricoEnderecoInicial(PessoaMonitorada custodiado, PessoaMonitoradaDTO dto) {
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

    private void criarPrimeiroComparecimento(PessoaMonitorada custodiado, Processo processo) {
        if (historicoComparecimentoRepository.existsCadastroInicialPorCustodiado(custodiado.getId())) return;
        HistoricoComparecimento c = HistoricoComparecimento.builder()
                .custodiado(custodiado).processo(processo)
                .dataComparecimento(processo.getDataComparecimentoInicial())
                .horaComparecimento(LocalTime.now()).tipoValidacao(TipoValidacao.CADASTRO_INICIAL)
                .validadoPor("Sistema SCC").observacoes("Cadastro inicial no sistema").mudancaEndereco(Boolean.FALSE).build();
        historicoComparecimentoRepository.save(c);
    }

    private void validarDadosPessoais(PessoaMonitoradaDTO dto) {
        if (dto.getNome() == null || dto.getNome().trim().isEmpty()) throw new IllegalArgumentException("Nome é obrigatório");
        boolean temCpf = dto.getCpf() != null && !dto.getCpf().trim().isEmpty();
        boolean temRg = dto.getRg() != null && !dto.getRg().trim().isEmpty();
        if (!temCpf && !temRg) throw new IllegalArgumentException("Pelo menos um documento (CPF ou RG) deve ser informado");
    }

    private void validarDadosObrigatorios(PessoaMonitoradaDTO dto) {
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

    private void validarEnderecoCompleto(PessoaMonitoradaDTO dto) {
        if (dto.getCep() == null || dto.getCep().trim().isEmpty()) throw new IllegalArgumentException("CEP é obrigatório");
        if (dto.getLogradouro() == null || dto.getLogradouro().trim().isEmpty()) throw new IllegalArgumentException("Logradouro é obrigatório");
        if (dto.getBairro() == null || dto.getBairro().trim().isEmpty()) throw new IllegalArgumentException("Bairro é obrigatório");
        if (dto.getCidade() == null || dto.getCidade().trim().isEmpty()) throw new IllegalArgumentException("Cidade é obrigatória");
        if (dto.getEstado() == null || dto.getEstado().trim().isEmpty()) throw new IllegalArgumentException("Estado é obrigatório");
    }

    private void validarFormatos(PessoaMonitoradaDTO dto) {
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

    private void validarDuplicidadesDocumentos(PessoaMonitoradaDTO dto) {
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty() && custodiadoRepository.existsByCpfAndSituacaoAtivo(dto.getCpf()))
            throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo");
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty() && custodiadoRepository.existsByRgAndSituacaoAtivo(dto.getRg().trim()))
            throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo");
    }

    private void validarDuplicidadesDocumentosParaUpdate(PessoaMonitoradaDTO dto, Long idAtual) {
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty() && custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(dto.getCpf(), idAtual))
            throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo");
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty() && custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(dto.getRg().trim(), idAtual))
            throw new IllegalArgumentException("RG já está cadastrado para outro custodiado ativo");
    }

    private void validarDatasLogicas(PessoaMonitoradaDTO dto) {
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
