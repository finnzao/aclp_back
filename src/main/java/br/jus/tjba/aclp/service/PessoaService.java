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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    @Transactional(readOnly = true)
    public List<Pessoa> findAll() {
        return pessoaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findById(Long id) {
        return pessoaRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findByProcesso(String processo) {
        return pessoaRepository.findByProcesso(processo);
    }

    @Transactional
    public Pessoa save(PessoaDTO dto) {
        log.info("Iniciando cadastro de pessoa: {}", dto.getNome());

        // Verificar se CPF já existe (se foi fornecido)
        if (dto.getCpf() != null && !dto.getCpf().trim().isEmpty()) {
            Optional<Pessoa> pessoaExistentePorCpf = pessoaRepository.findByCpf(dto.getCpf());
            if (pessoaExistentePorCpf.isPresent()) {
                log.error("CPF já cadastrado: {}", dto.getCpf());
                throw new IllegalArgumentException("Este CPF já está cadastrado no sistema");
            }
        }

        // Verificar se RG já existe (se foi fornecido)
        if (dto.getRg() != null && !dto.getRg().trim().isEmpty()) {
            Optional<Pessoa> pessoaExistentePorRg = pessoaRepository.findByRg(dto.getRg());
            if (pessoaExistentePorRg.isPresent()) {
                log.error("RG já cadastrado: {}", dto.getRg());
                throw new IllegalArgumentException("Este RG já está cadastrado no sistema");
            }
        }

        // Verificar se pelo menos CPF ou RG foi fornecido
        if ((dto.getCpf() == null || dto.getCpf().trim().isEmpty()) &&
                (dto.getRg() == null || dto.getRg().trim().isEmpty())) {
            log.error("Nem CPF nem RG foram fornecidos para: {}", dto.getNome());
            throw new IllegalArgumentException("É obrigatório informar pelo menos CPF ou RG");
        }

        // Criar endereço se os dados foram fornecidos
        Endereco endereco = null;
        if (dto.getCep() != null && !dto.getCep().trim().isEmpty()) {
            log.info("Criando endereço para pessoa");
            endereco = Endereco.builder()
                    .cep(dto.getCep())
                    .logradouro(dto.getLogradouro())
                    .numero(dto.getNumero())
                    .complemento(dto.getComplemento())
                    .bairro(dto.getBairro())
                    .cidade(dto.getCidade())
                    .estado(dto.getEstado())
                    .criadoEm(LocalDateTime.now())
                    .version(0L)
                    .build();
        }

        // Criar pessoa
        log.info("Criando pessoa");
        Pessoa pessoa = Pessoa.builder()
                .nome(dto.getNome())
                .cpf(dto.getCpf())
                .rg(dto.getRg())
                .contato(dto.getContato())
                .processo(dto.getProcesso())
                .vara(dto.getVara())
                .comarca(dto.getComarca())
                .dataDecisao(dto.getDataDecisao())
                .periodicidade(dto.getPeriodicidade())
                .dataComparecimentoInicial(dto.getDataComparecimentoInicial())
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .primeiroComparecimento(dto.getDataComparecimentoInicial())
                .ultimoComparecimento(dto.getDataComparecimentoInicial())
                .observacoes(dto.getObservacoes())
                .endereco(endereco)
                .criadoEm(LocalDateTime.now())
                .version(0L)
                .build();

        // Calcular próximo comparecimento
        pessoa.calcularProximoComparecimento();

        log.info("Salvando pessoa no banco de dados");
        Pessoa pessoaSalva = pessoaRepository.save(pessoa);
        log.info("Pessoa salva com sucesso. ID: {}", pessoaSalva.getId());

        return pessoaSalva;
    }

    @Transactional(readOnly = true)
    public List<Pessoa> findByStatus(StatusComparecimento status) {
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
        return pessoaRepository.buscarPorNomeOuProcesso(termo, termo);
    }

    @Transactional(readOnly = true)
    public long countByStatus(StatusComparecimento status) {
        return pessoaRepository.countByStatus(status);
    }
}