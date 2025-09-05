package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.PessoaDTO;
import br.jus.tjba.aclp.model.Endereco;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.PessoaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    @Transactional(readOnly = true)
    public List<Pessoa> findAll() {
        log.info("Buscando todas as pessoas cadastradas");
        return pessoaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findById(Long id) {
        log.info("Buscando pessoa por ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        return pessoaRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findByProcesso(String processo) {
        log.info("Buscando pessoa por processo: {}", processo);

        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }

        return pessoaRepository.findByProcesso(processo.trim());
    }

    @Transactional
    public Pessoa save(PessoaDTO dto) {
        log.info("Iniciando cadastro de nova pessoa - Processo: {}", dto.getProcesso());

        // Limpar e formatar dados antes das validações
        dto.limparEFormatarDados();

        // Validações usando IllegalArgumentException - o GlobalExceptionHandler vai capturar
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidades(dto);
        validarDatasLogicas(dto);
        validarEnderecoCompleto(dto); // Nova validação para endereço obrigatório

        // Criar endereço (agora obrigatório)
        Endereco endereco = criarEndereco(dto);

        // Criar pessoa
        Pessoa pessoa = Pessoa.builder()
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
                .primeiroComparecimento(dto.getDataComparecimentoInicial())
                .ultimoComparecimento(dto.getDataComparecimentoInicial())
                .observacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null)
                .endereco(endereco)
                .build();

        // Calcular próximo comparecimento
        pessoa.calcularProximoComparecimento();

        Pessoa pessoaSalva = pessoaRepository.save(pessoa);
        log.info("Pessoa cadastrada com sucesso - ID: {}, Nome: {}, Endereço: {}",
                pessoaSalva.getId(), pessoaSalva.getNome(), pessoaSalva.getEndereco().getEnderecoResumido());

        return pessoaSalva;
    }

    @Transactional
    public Pessoa update(Long id, PessoaDTO dto) {
        log.info("Atualizando pessoa ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Pessoa pessoa = pessoaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada com ID: " + id));

        // Limpar e formatar dados antes das validações
        dto.limparEFormatarDados();

        // Validações (excluindo duplicidades do próprio registro)
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidadesParaUpdate(dto, id);
        validarDatasLogicas(dto);
        validarEnderecoCompleto(dto); // Endereço agora é obrigatório também na atualização

        // Atualizar dados básicos
        pessoa.setNome(dto.getNome().trim());
        pessoa.setCpf(dto.getCpf());
        pessoa.setRg(dto.getRg());
        pessoa.setContato(dto.getContato());
        pessoa.setProcesso(dto.getProcesso().trim());
        pessoa.setVara(dto.getVara().trim());
        pessoa.setComarca(dto.getComarca().trim());
        pessoa.setDataDecisao(dto.getDataDecisao());
        pessoa.setPeriodicidade(dto.getPeriodicidade());
        pessoa.setDataComparecimentoInicial(dto.getDataComparecimentoInicial());
        pessoa.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes().trim() : null);

        // Atualizar endereço (agora sempre obrigatório)
        if (pessoa.getEndereco() == null) {
            pessoa.setEndereco(criarEndereco(dto));
        } else {
            atualizarEndereco(pessoa.getEndereco(), dto);
        }

        // Recalcular próximo comparecimento se necessário
        pessoa.calcularProximoComparecimento();

        Pessoa pessoaAtualizada = pessoaRepository.save(pessoa);
        log.info("Pessoa atualizada com sucesso - ID: {}, Nome: {}, Endereço: {}",
                pessoaAtualizada.getId(), pessoaAtualizada.getNome(),
                pessoaAtualizada.getEndereco().getEnderecoResumido());

        return pessoaAtualizada;
    }

    @Transactional
    public void delete(Long id) {
        log.info("Excluindo pessoa ID: {}", id);

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }

        Pessoa pessoa = pessoaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada com ID: " + id));

        // Verificar se há dependências que impedem a exclusão
        if (pessoa.getHistoricoComparecimentos() != null && !pessoa.getHistoricoComparecimentos().isEmpty()) {
            throw new IllegalArgumentException("Não é possível excluir pessoa que possui histórico de comparecimentos");
        }

        pessoaRepository.delete(pessoa);
        log.info("Pessoa excluída com sucesso - ID: {}", id);
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findByStatus(StatusComparecimento status) {
        log.info("Buscando pessoas por status: {}", status);

        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório. Use: EM_CONFORMIDADE ou INADIMPLENTE");
        }

        return pessoaRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findComparecimentosHoje() {
        log.info("Buscando pessoas com comparecimento hoje");
        return pessoaRepository.findByProximoComparecimento(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findAtrasados() {
        log.info("Buscando pessoas em atraso");
        return pessoaRepository.findByProximoComparecimentoBefore(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Pessoa> buscarPorNomeOuProcesso(String termo) {
        log.info("Buscando pessoas por termo: {}", termo);

        if (termo == null || termo.trim().isEmpty()) {
            throw new IllegalArgumentException("Termo de busca é obrigatório");
        }

        String termoLimpo = termo.trim();
        if (termoLimpo.length() < 2) {
            throw new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres");
        }

        return pessoaRepository.buscarPorNomeOuProcesso(termoLimpo, termoLimpo);
    }

    // ========== MÉTODOS PRIVADOS DE VALIDAÇÃO ==========

    private void validarDadosObrigatorios(PessoaDTO dto) {
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

    /**
     * Nova validação para endereço completo obrigatório
     */
    private void validarEnderecoCompleto(PessoaDTO dto) {
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

        // Validar se todos os campos obrigatórios de endereço estão preenchidos
        if (!dto.hasEnderecoCompleto()) {
            throw new IllegalArgumentException("Endereço completo é obrigatório. Preencha: CEP, logradouro, bairro, cidade e estado");
        }
    }

    private void validarFormatos(PessoaDTO dto) {
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

        // === VALIDAÇÕES DE ENDEREÇO ===

        // Validar CEP
        if (dto.getCep() != null && !dto.getCep().trim().isEmpty()) {
            if (!validarFormatoCep(dto.getCep().trim())) {
                throw new IllegalArgumentException("CEP deve ter formato válido (00000-000 ou apenas números)");
            }
        }

        // Validar logradouro
        if (dto.getLogradouro() != null && !dto.getLogradouro().trim().isEmpty()) {
            String logradouro = dto.getLogradouro().trim();
            if (logradouro.length() < 5 || logradouro.length() > 200) {
                throw new IllegalArgumentException("Logradouro deve ter entre 5 e 200 caracteres");
            }
        }

        // Validar bairro
        if (dto.getBairro() != null && !dto.getBairro().trim().isEmpty()) {
            String bairro = dto.getBairro().trim();
            if (bairro.length() < 2 || bairro.length() > 100) {
                throw new IllegalArgumentException("Bairro deve ter entre 2 e 100 caracteres");
            }
        }

        // Validar cidade
        if (dto.getCidade() != null && !dto.getCidade().trim().isEmpty()) {
            String cidade = dto.getCidade().trim();
            if (cidade.length() < 2 || cidade.length() > 100) {
                throw new IllegalArgumentException("Cidade deve ter entre 2 e 100 caracteres");
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

        // Validar número (se fornecido)
        if (dto.getNumero() != null && !dto.getNumero().trim().isEmpty()) {
            if (dto.getNumero().trim().length() > 20) {
                throw new IllegalArgumentException("Número deve ter no máximo 20 caracteres");
            }
        }

        // Validar complemento (se fornecido)
        if (dto.getComplemento() != null && !dto.getComplemento().trim().isEmpty()) {
            if (dto.getComplemento().trim().length() > 100) {
                throw new IllegalArgumentException("Complemento deve ter no máximo 100 caracteres");
            }
        }
    }

    private void validarDuplicidades(PessoaDTO dto) {
        // Verificar CPF duplicado (se fornecido)
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);
            Optional<Pessoa> pessoaComCpf = pessoaRepository.findByCpf(cpfFormatado);
            if (pessoaComCpf.isPresent()) {
                throw new IllegalArgumentException("CPF já está cadastrado no sistema");
            }
        }

        // Verificar RG duplicado (se fornecido)
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            Optional<Pessoa> pessoaComRg = pessoaRepository.findByRg(dto.getRg().trim());
            if (pessoaComRg.isPresent()) {
                throw new IllegalArgumentException("RG já está cadastrado no sistema");
            }
        }

        // Verificar processo duplicado
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty()) {
            Optional<Pessoa> pessoaComProcesso = pessoaRepository.findByProcesso(dto.getProcesso().trim());
            if (pessoaComProcesso.isPresent()) {
                throw new IllegalArgumentException("Processo já está cadastrado no sistema");
            }
        }
    }

    private void validarDuplicidadesParaUpdate(PessoaDTO dto, Long idAtual) {
        // Verificar CPF duplicado em outro registro
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            String cpfFormatado = formatarCpf(cpf);
            Optional<Pessoa> pessoaComCpf = pessoaRepository.findByCpf(cpfFormatado);
            if (pessoaComCpf.isPresent() && !pessoaComCpf.get().getId().equals(idAtual)) {
                throw new IllegalArgumentException("CPF já está cadastrado no sistema");
            }
        }

        // Verificar RG duplicado em outro registro
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            Optional<Pessoa> pessoaComRg = pessoaRepository.findByRg(dto.getRg().trim());
            if (pessoaComRg.isPresent() && !pessoaComRg.get().getId().equals(idAtual)) {
                throw new IllegalArgumentException("RG já está cadastrado no sistema");
            }
        }

        // Verificar processo duplicado em outro registro
        if (dto.getProcesso() != null && !dto.getProcesso().trim().isEmpty()) {
            Optional<Pessoa> pessoaComProcesso = pessoaRepository.findByProcesso(dto.getProcesso().trim());
            if (pessoaComProcesso.isPresent() && !pessoaComProcesso.get().getId().equals(idAtual)) {
                throw new IllegalArgumentException("Processo já está cadastrado no sistema");
            }
        }
    }

    private void validarDatasLogicas(PessoaDTO dto) {
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

    // ========== MÉTODOS DE CRIAÇÃO/ATUALIZAÇÃO DE ENDEREÇO ==========

    /**
     * Cria um endereço completo (agora obrigatório)
     */
    private Endereco criarEndereco(PessoaDTO dto) {
        return Endereco.builder()
                .cep(dto.getCep().trim())
                .logradouro(dto.getLogradouro().trim())
                .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                .bairro(dto.getBairro().trim())
                .cidade(dto.getCidade().trim())
                .estado(dto.getEstado().trim().toUpperCase())
                .build();
    }

    private void atualizarEndereco(Endereco endereco, PessoaDTO dto) {
        endereco.setCep(dto.getCep().trim());
        endereco.setLogradouro(dto.getLogradouro().trim());
        endereco.setNumero(dto.getNumero() != null ? dto.getNumero().trim() : null);
        endereco.setComplemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null);
        endereco.setBairro(dto.getBairro().trim());
        endereco.setCidade(dto.getCidade().trim());
        endereco.setEstado(dto.getEstado().trim().toUpperCase());
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

    /**
     * Valida formato do CEP
     */
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