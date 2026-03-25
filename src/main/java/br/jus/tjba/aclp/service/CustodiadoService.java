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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustodiadoService {

    private final CustodiadoRepository custodiadoRepository;
    private final HistoricoComparecimentoRepository historicoComparecimentoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;

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
        log.info("Custodiado arquivado — publicId: {}, Nome: {}", publicIdStr, custodiado.getNome());
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAll() {
        return custodiadoRepository.findAllWithEnderecosAtivos();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllIncludingArchived() {
        return custodiadoRepository.findAllIncludingArchivedWithEnderecos();
    }

    @Transactional(readOnly = true)
    public Optional<Custodiado> findById(Long id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID deve ser um número positivo");
        return custodiadoRepository.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID deve ser um número positivo");
        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Custodiado já está arquivado");
        custodiado.arquivar();
        custodiadoRepository.save(custodiado);
        log.info("Custodiado arquivado — ID: {}, Nome: {}", id, custodiado.getNome());
    }

    @Transactional
    public Custodiado reativar(Long id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID deve ser um número positivo");
        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));
        if (!custodiado.isArquivado())
            throw new IllegalArgumentException("Custodiado já está ativo");
        validarDuplicidadesDocumentosParaReativacao(custodiado);
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
        log.info("Iniciando cadastro de novo custodiado — Processo: {}, Nome: {}",
                dto.getProcesso(), dto.getNome());

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
        Custodiado salvo = custodiadoRepository.save(custodiado);

        criarHistoricoEnderecoInicial(salvo, dto);
        criarPrimeiroComparecimento(salvo);

        log.info("Custodiado cadastrado com sucesso — publicId: {}, ID interno: {}",
                salvo.getPublicId(), salvo.getId());
        return salvo;
    }

    @Transactional
    public Custodiado update(Long id, CustodiadoDTO dto) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("ID deve ser um número positivo");
        Custodiado custodiado = custodiadoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrado com ID: " + id));
        return atualizarCustodiado(custodiado, dto);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findByStatus(StatusComparecimento status) {
        if (status == null)
            throw new IllegalArgumentException("Status é obrigatório. Use: EM_CONFORMIDADE ou INADIMPLENTE");
        return custodiadoRepository.findByStatusWithEnderecos(status);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findComparecimentosHoje() {
        return custodiadoRepository.findComparecimentosHoje();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findInadimplentes() {
        return custodiadoRepository.findInadimplentesWithEnderecos();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> buscarPorNomeOuProcesso(String termo) {
        if (termo == null || termo.trim().isEmpty())
            throw new IllegalArgumentException("Termo de busca é obrigatório");
        if (termo.trim().length() < 2)
            throw new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres");
        String termoLimpo = termo.trim();
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

    @Transactional(readOnly = true)
    public List<Custodiado> findBySituacao(SituacaoCustodiado situacao) {
        return custodiadoRepository.findBySituacao(situacao);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllArchived() {
        return custodiadoRepository.findAllArchived();
    }

    @Transactional(readOnly = true)
    public long countBySituacao(SituacaoCustodiado situacao) {
        return custodiadoRepository.countBySituacao(situacao);
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findReactivatable() {
        return custodiadoRepository.findReactivatable();
    }

    @Transactional(readOnly = true)
    public List<Custodiado> findAllActive() {
        return custodiadoRepository.findAllActive();
    }

    public String getEnderecoCompleto(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);
        return enderecoAtivo.map(HistoricoEndereco::getEnderecoCompleto).orElse("Endereço não informado");
    }

    public String getEnderecoResumido(Long custodiadoId) {
        Optional<HistoricoEndereco> enderecoAtivo =
                historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(custodiadoId);
        return enderecoAtivo.map(HistoricoEndereco::getEnderecoResumido).orElse("Sem endereço");
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

    private Custodiado atualizarCustodiado(Custodiado custodiado, CustodiadoDTO dto) {
        if (custodiado.isArquivado())
            throw new IllegalArgumentException("Não é possível atualizar custodiado arquivado. Reative-o primeiro.");

        dto.limparEFormatarDados();

        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty())
            dto.setCpf(formatarCpf(dto.getCpf().trim()));
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty())
            dto.setProcesso(formatarProcesso(dto.getProcesso().trim()));

        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesDocumentosParaUpdate(dto, custodiado.getId());
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
        log.info("Custodiado atualizado com sucesso — publicId: {}, Nome: {}",
                custodiadoAtualizado.getPublicId(), custodiadoAtualizado.getNome());
        return custodiadoAtualizado;
    }

    private void criarHistoricoEnderecoInicial(Custodiado custodiado, CustodiadoDTO dto) {
        try {
            historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(custodiado.getId());

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

            log.info("Histórico de endereço inicial criado — ID: {}, Custodiado: {}",
                    enderecoSalvo.getId(), custodiado.getNome());
        } catch (Exception e) {
            log.error("Erro ao criar histórico de endereço inicial para custodiado ID: " + custodiado.getId(), e);
            throw new RuntimeException("Erro ao criar endereço inicial: " + e.getMessage(), e);
        }
    }

    private void criarPrimeiroComparecimento(Custodiado custodiado) {
        try {
            boolean jaTemComparecimento = historicoComparecimentoRepository
                    .existsCadastroInicialPorCustodiado(custodiado.getId());

            if (jaTemComparecimento) return;

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
            log.info("Primeiro comparecimento registrado — ID: {}, Custodiado: {}",
                    comparecimentoSalvo.getId(), custodiado.getNome());
        } catch (Exception e) {
            log.error("Erro ao criar primeiro comparecimento para custodiado ID: " + custodiado.getId(), e);
            throw new RuntimeException("Erro ao criar primeiro comparecimento: " + e.getMessage(), e);
        }
    }

    private void validarDadosObrigatorios(CustodiadoDTO dto) {
        if (dto.getNome() == null || dto.getNome().trim().isEmpty())
            throw new IllegalArgumentException("Nome é obrigatório");
        if (dto.getContato() == null || dto.getContato().trim().isEmpty())
            throw new IllegalArgumentException("Contato é obrigatório");
        if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty())
            throw new IllegalArgumentException("Número do processo é obrigatório");
        if (dto.getVara() == null || dto.getVara().trim().isEmpty())
            throw new IllegalArgumentException("Vara é obrigatória");
        if (dto.getComarca() == null || dto.getComarca().trim().isEmpty())
            throw new IllegalArgumentException("Comarca é obrigatória");
        if (dto.getDataDecisao() == null)
            throw new IllegalArgumentException("Data da decisão é obrigatória");
        if (dto.getPeriodicidade() == null || dto.getPeriodicidade() <= 0)
            throw new IllegalArgumentException("Periodicidade deve ser um número positivo (em dias)");
        if ((dto.getCpf() == null || dto.getCpf().trim().isEmpty())
                && (dto.getRg() == null || dto.getRg().trim().isEmpty()))
            throw new IllegalArgumentException("Pelo menos um documento (CPF ou RG) deve ser informado");
    }

    private void validarEnderecoCompleto(CustodiadoDTO dto) {
        if (dto.getCep() == null || dto.getCep().trim().isEmpty())
            throw new IllegalArgumentException("CEP é obrigatório");
        if (dto.getLogradouro() == null || dto.getLogradouro().trim().isEmpty())
            throw new IllegalArgumentException("Logradouro é obrigatório");
        if (dto.getBairro() == null || dto.getBairro().trim().isEmpty())
            throw new IllegalArgumentException("Bairro é obrigatório");
        if (dto.getCidade() == null || dto.getCidade().trim().isEmpty())
            throw new IllegalArgumentException("Cidade é obrigatória");
        if (dto.getEstado() == null || dto.getEstado().trim().isEmpty())
            throw new IllegalArgumentException("Estado é obrigatório");
        if (!dto.hasEnderecoCompleto())
            throw new IllegalArgumentException("Endereço completo é obrigatório. Preencha: CEP, logradouro, bairro, cidade e estado");
    }

    private void validarFormatos(CustodiadoDTO dto) {
        if (dto.getNome() != null && dto.getNome().trim().length() > 150)
            throw new IllegalArgumentException("Nome deve ter no máximo 150 caracteres");
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = dto.getCpf().trim();
            if (!validarCpfAlgoritmo(cpf))
                throw new IllegalArgumentException("CPF inválido. Verifique os dígitos verificadores");
        }
        if (dto.getProcesso() != null && !validarFormatoProcesso(dto.getProcesso().trim()))
            throw new IllegalArgumentException("Processo deve ter formato válido (0000000-00.0000.0.00.0000)");
        if (dto.getPeriodicidade() != null && (dto.getPeriodicidade() < 1 || dto.getPeriodicidade() > 365))
            throw new IllegalArgumentException("Periodicidade deve estar entre 1 e 365 dias");
        if (dto.getContato() != null && !validarFormatoContatoFlexivel(dto.getContato().trim()))
            throw new IllegalArgumentException("Contato deve ter formato válido de telefone");
        if (dto.getCep() != null && !dto.getCep().trim().isEmpty()) {
            if (!validarFormatoCep(dto.getCep().trim()))
                throw new IllegalArgumentException("CEP deve ter formato válido (00000-000 ou apenas números)");
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
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            if (custodiadoRepository.existsByCpfAndSituacaoAtivo(dto.getCpf()))
                throw new IllegalArgumentException("CPF já está cadastrado para um custodiado ativo no sistema");
        }
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivo(dto.getRg().trim()))
                throw new IllegalArgumentException("RG já está cadastrado para um custodiado ativo no sistema");
        }
    }

    private void validarDuplicidadesDocumentosParaUpdate(CustodiadoDTO dto, Long idAtual) {
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            if (custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(dto.getCpf(), idAtual))
                throw new IllegalArgumentException("CPF já está cadastrado para outro custodiado ativo no sistema");
        }
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(dto.getRg().trim(), idAtual))
                throw new IllegalArgumentException("RG já está cadastrado para outro custodiado ativo no sistema");
        }
    }

    private void validarDuplicidadesDocumentosParaReativacao(Custodiado custodiado) {
        if (custodiado.getCpf() != null && !custodiado.getCpf().trim().isEmpty()) {
            if (custodiadoRepository.existsByCpfAndSituacaoAtivoAndIdNot(custodiado.getCpf(), custodiado.getId()))
                throw new IllegalArgumentException("Não é possível reativar: CPF já está em uso por outro custodiado ativo");
        }
        if (custodiado.getRg() != null && !custodiado.getRg().trim().isEmpty()) {
            if (custodiadoRepository.existsByRgAndSituacaoAtivoAndIdNot(custodiado.getRg(), custodiado.getId()))
                throw new IllegalArgumentException("Não é possível reativar: RG já está em uso por outro custodiado ativo");
        }
    }

    private void validarDatasLogicas(CustodiadoDTO dto) {
        LocalDate hoje = LocalDate.now();
        if (dto.getDataDecisao() != null && dto.getDataDecisao().isAfter(hoje))
            throw new IllegalArgumentException("Data da decisão não pode ser uma data futura");
        if (dto.getDataComparecimentoInicial() != null && dto.getDataDecisao() != null) {
            if (dto.getDataComparecimentoInicial().isBefore(dto.getDataDecisao()))
                throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior à data da decisão");
        }
        if (dto.getDataComparecimentoInicial() != null
                && dto.getDataComparecimentoInicial().isBefore(hoje.minusYears(5)))
            throw new IllegalArgumentException("Data do comparecimento inicial não pode ser anterior a 5 anos");
    }

    private boolean validarCpfAlgoritmo(String cpf) {
        String cpfLimpo = limparCpf(cpf);
        if (cpfLimpo.length() != 11) return false;
        if (cpfLimpo.matches("(\\d)\\1{10}")) return false;
        try {
            int[] digitos = new int[11];
            for (int i = 0; i < 11; i++)
                digitos[i] = Character.getNumericValue(cpfLimpo.charAt(i));
            int soma1 = 0;
            for (int i = 0; i < 9; i++) soma1 += digitos[i] * (10 - i);
            int resto1 = soma1 % 11;
            int dv1 = (resto1 < 2) ? 0 : (11 - resto1);
            if (digitos[9] != dv1) return false;
            int soma2 = 0;
            for (int i = 0; i < 10; i++) soma2 += digitos[i] * (11 - i);
            int resto2 = soma2 % 11;
            int dv2 = (resto2 < 2) ? 0 : (11 - resto2);
            return digitos[10] == dv2;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validarFormatoProcesso(String processo) {
        return processo.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}");
    }

    private String formatarProcesso(String processo) {
        String numeros = processo.replaceAll("[^\\d]", "");
        if (numeros.length() != 20) return processo.trim();
        try {
            return String.format("%s-%s.%s.%s.%s.%s",
                    numeros.substring(0, 7), numeros.substring(7, 9), numeros.substring(9, 13),
                    numeros.substring(13, 14), numeros.substring(14, 16), numeros.substring(16, 20));
        } catch (Exception e) {
            return processo.trim();
        }
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
        if (cpfLimpo.length() != 11) return cpf.trim();
        if (!validarCpfAlgoritmo(cpfLimpo))
            throw new IllegalArgumentException("CPF inválido. Verifique os dígitos verificadores");
        return cpfLimpo.substring(0, 3) + "." + cpfLimpo.substring(3, 6) + "."
                + cpfLimpo.substring(6, 9) + "-" + cpfLimpo.substring(9);
    }

    private String formatarCep(String cep) {
        if (cep == null) return null;
        String cepLimpo = cep.replaceAll("[^\\d]", "");
        if (cepLimpo.length() == 8) {
            return cepLimpo.substring(0, 5) + "-" + cepLimpo.substring(5);
        }
        return cep;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new EntityNotFoundException("Identificador inválido: " + value);
        }
    }
}
