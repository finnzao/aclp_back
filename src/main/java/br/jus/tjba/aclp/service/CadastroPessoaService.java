package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.CadastroInicialDTO;
import br.jus.tjba.aclp.dto.CadastroInicialResponseDTO;
import br.jus.tjba.aclp.dto.PessoaMonitoradaDTO;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.NaturezaVinculo;
import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.repository.HistoricoComparecimentoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import br.jus.tjba.aclp.util.Formatadores;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Fluxo de cadastro e edicao de pessoas: validacoes, formatacoes e criacao em transacao unica. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CadastroPessoaService {

    private final PessoaMonitoradaRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final ProcessoRepository processoRepository;
    private final ExecucaoPenalService execucaoPenalService;

    @Transactional
    public CadastroInicialResponseDTO cadastroInicial(CadastroInicialDTO dto) {
        log.info("=== CADASTRO INICIAL === Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        dto.limparEFormatarDados();
        validarCadastroInicial(dto);

        String cpfFormatado = (dto.getCpf() != null && !dto.getCpf().trim().isEmpty())
                ? formatarCpf(dto.getCpf().trim()) : null;
        String processoFormatado = Formatadores.formatarProcesso(dto.getProcesso().trim());
        String contatoFinal = dto.getContatoEfetivo();
        LocalDate dataCompInicial = dto.getDataComparecimentoInicial() != null
                ? dto.getDataComparecimentoInicial() : LocalDate.now();

        validarDuplicidadesDocumentosCadastroInicial(cpfFormatado, dto.getRg());

        boolean execucao = dto.getNaturezaVinculo() == NaturezaVinculo.EXECUCAO_PENAL;
        if (execucao && dto.getPena() == null)
            throw new IllegalArgumentException(
                    "Cadastro de execução de pena exige o bloco 'pena' com os dados da sentença.");
        boolean temAgenda = !execucao || dto.getPena().getModalidade() == null
                || dto.getPena().getModalidade().isComparecimentoPeriodico();
        boolean temEndereco = temTexto(dto.getCep());
        if (temAgenda && dto.getPeriodicidade() == null)
            throw new IllegalArgumentException("Periodicidade é obrigatória nesta modalidade");
        if (!execucao && dto.getDataDecisao() == null)
            throw new IllegalArgumentException("Data da decisão é obrigatória");
        if (!execucao && !temEndereco)
            throw new IllegalArgumentException("Endereço é obrigatório");
        if (temEndereco && (!temTexto(dto.getLogradouro()) || !temTexto(dto.getBairro())
                || !temTexto(dto.getCidade()) || !temTexto(dto.getEstado())))
            throw new IllegalArgumentException("Endereço incompleto: logradouro, bairro, cidade e UF");
        LocalDate dataDecisao = dto.getDataDecisao() != null ? dto.getDataDecisao()
                : (execucao ? dto.getPena().getDataInicio() : null);

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
                .dataDecisao(dataDecisao)
                .periodicidade(temAgenda ? dto.getPeriodicidade() : null)
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
            dto.getPena().setProcessoId(processoSalvo.getId());
            execucaoPenalService.registrarPena(dto.getPena());
        }

        HistoricoEndereco enderecoSalvo = null;
        if (temEndereco) {
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

        enderecoSalvo = historicoEnderecoRepository.save(endereco);
        }

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
                .enderecoCompleto(enderecoSalvo != null ? enderecoSalvo.getEnderecoCompleto() : null)
                .enderecoResumido(enderecoSalvo != null ? enderecoSalvo.getEnderecoResumido() : null)
                .cidade(enderecoSalvo != null ? enderecoSalvo.getCidade() : null)
                .estado(enderecoSalvo != null ? enderecoSalvo.getEstado() : null)
                .observacoes(custodiadoSalvo.getObservacoes())
                .criadoEm(custodiadoSalvo.getCriadoEm())
                .build();
    }

    // =====================================================================
    // Validações (inalteradas)
    // =====================================================================

    private static boolean temTexto(String s) { return s != null && !s.trim().isEmpty(); }

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
        String processoFormatado = Formatadores.formatarProcesso(dto.getProcesso().trim());
        if (!validarFormatoProcesso(processoFormatado))
            throw new IllegalArgumentException("Processo deve ter formato válido (0000000-00.0000.0.00.0000)");

        if (dto.getVara() == null || dto.getVara().trim().isEmpty())
            throw new IllegalArgumentException("Vara é obrigatória");
        if (dto.getComarca() == null || dto.getComarca().trim().isEmpty())
            throw new IllegalArgumentException("Comarca é obrigatória");

        // obrigatoriedade por natureza fica em cadastroInicial; aqui so formato
        if (dto.getDataDecisao() != null && dto.getDataDecisao().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Data da decisão não pode ser futura");
        if (dto.getDataDecisao() != null && dto.getDataComparecimentoInicial() != null
                && dto.getDataComparecimentoInicial().isBefore(dto.getDataDecisao()))
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior à data da decisão");

        if (dto.getPeriodicidade() != null && (dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365))
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");

        if (temTexto(dto.getCep()) && !validarFormatoCep(dto.getCep().trim()))
            throw new IllegalArgumentException("CEP deve ter formato válido");
        if (temTexto(dto.getEstado())) {
            try {
                EstadoBrasil.fromString(dto.getEstado().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Estado inválido: " + dto.getEstado());
            }
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


    @Transactional
    public PessoaMonitorada updateByPublicId(String publicIdStr, PessoaMonitoradaDTO dto) {
        UUID publicId = parseUuid(publicIdStr);
        PessoaMonitorada custodiado = custodiadoRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa monitorada não encontrada: " + publicIdStr));
        return atualizarPessoaMonitorada(custodiado, dto);
    }


    @Transactional
    public PessoaMonitorada save(PessoaMonitoradaDTO dto) {
        log.info("Cadastrando custodiado (endpoint legado) — Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        dto.setId(null);
        dto.limparEFormatarDados();

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty())
            dto.setCpf(formatarCpf(dto.getCpf().trim()));
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty())
            dto.setProcesso(Formatadores.formatarProcesso(dto.getProcesso().trim()));
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
