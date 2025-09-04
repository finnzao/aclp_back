package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.PessoaDTO;
import br.jus.tjba.aclp.model.Endereco;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.PessoaRepository;
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
        return pessoaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID deve ser um número positivo");
        }
        return pessoaRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findByProcesso(String processo) {
        if (processo == null || processo.trim().isEmpty()) {
            throw new IllegalArgumentException("Número do processo é obrigatório");
        }
        return pessoaRepository.findByProcesso(processo.trim());
    }

    @Transactional
    public Pessoa save(PessoaDTO dto) {
        log.info("Iniciando cadastro de nova pessoa - Processo: {}", dto.getProcesso());

        // Validações centralizadas usando IllegalArgumentException
        validarDadosObrigatorios(dto);
        validarFormatos(dto);
        validarDuplicidades(dto);
        validarDatasLogicas(dto);

        try {
            // Criar endereço se fornecido
            Endereco endereco = criarEnderecoSeNecessario(dto);

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
            log.info("Pessoa cadastrada com sucesso - ID: {}, Nome: {}", pessoaSalva.getId(), pessoaSalva.getNome());

            return pessoaSalva;

        } catch (Exception e) {
            log.error("Erro ao salvar pessoa: {}", e.getMessage());
            throw e; // O GlobalExceptionHandler vai capturar e tratar
        }
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findByStatus(StatusComparecimento status) {
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório. Use: EM_CONFORMIDADE ou INADIMPLENTE");
        }
        return pessoaRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findComparecimentosHoje() {
        return pessoaRepository.findByProximoComparecimento(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findAtrasados() {
        return pessoaRepository.findByProximoComparecimentoBefore(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Pessoa> buscarPorNomeOuProcesso(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            throw new IllegalArgumentException("Termo de busca é obrigatório e deve ter ao menos 2 caracteres");
        }

        String termoLimpo = termo.trim();
        if (termoLimpo.length() < 2) {
            throw new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres");
        }

        return pessoaRepository.buscarPorNomeOuProcesso(termoLimpo, termoLimpo);
    }

    // Métodos privados de validação - usando apenas IllegalArgumentException

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

    private void validarFormatos(PessoaDTO dto) {
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
    }

    private void validarDuplicidades(PessoaDTO dto) {
        // Verificar CPF duplicado (se fornecido)
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            String cpf = limparCpf(dto.getCpf().trim());
            Optional<Pessoa> pessoaComCpf = pessoaRepository.findByCpf(formatarCpf(cpf));
            if (pessoaComCpf.isPresent()) {
                throw new IllegalArgumentException("CPF já está cadastrado no sistema: " + formatarCpf(cpf));
            }
        }

        // Verificar RG duplicado (se fornecido) - quando implementar findByRg
        // if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
        //     Optional<Pessoa> pessoaComRg = pessoaRepository.findByRg(dto.getRg().trim());
        //     if (pessoaComRg.isPresent()) {
        //         throw new IllegalArgumentException("RG já está cadastrado no sistema: " + dto.getRg());
        //     }
        // }
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
    }

    private Endereco criarEnderecoSeNecessario(PessoaDTO dto) {
        if (dto.getCep() == null || dto.getCep().trim().isEmpty()) {
            return null;
        }

        return Endereco.builder()
                .cep(dto.getCep().trim())
                .logradouro(dto.getLogradouro() != null ? dto.getLogradouro().trim() : null)
                .numero(dto.getNumero() != null ? dto.getNumero().trim() : null)
                .complemento(dto.getComplemento() != null ? dto.getComplemento().trim() : null)
                .bairro(dto.getBairro() != null ? dto.getBairro().trim() : null)
                .cidade(dto.getCidade() != null ? dto.getCidade().trim() : null)
                .estado(dto.getEstado() != null ? dto.getEstado().trim().toUpperCase() : null)
                .build();
    }

    // Métodos utilitários

    private boolean validarFormatoCpf(String cpf) {
        String cpfLimpo = limparCpf(cpf);
        return cpfLimpo.matches("\\d{11}");
    }

    private boolean validarFormatoProcesso(String processo) {
        return processo.matches("\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}");
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