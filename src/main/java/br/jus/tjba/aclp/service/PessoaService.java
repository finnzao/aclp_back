package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.PessoaDTO;
import br.jus.tjba.aclp.model.Endereco;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        return pessoaRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Pessoa> findByProcesso(String processo) {
        return pessoaRepository.findByProcesso(processo);
    }

    @Transactional
    public Pessoa save(PessoaDTO dto) {
        // Verificar se processo já existe
        if (pessoaRepository.findByProcesso(dto.getProcesso()).isPresent()) {
            throw new IllegalArgumentException("Processo já cadastrado");
        }

        // Criar endereço
        Endereco endereco = null;
        if (dto.getCep() != null && !dto.getCep().isEmpty()) {
            endereco = Endereco.builder()
                    .cep(dto.getCep())
                    .logradouro(dto.getLogradouro())
                    .numero(dto.getNumero())
                    .complemento(dto.getComplemento())
                    .bairro(dto.getBairro())
                    .cidade(dto.getCidade())
                    .estado(dto.getEstado())
                    .build();
        }

        // Criar pessoa
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
                .build();

        // Calcular próximo comparecimento
        pessoa.calcularProximoComparecimento();

        return pessoaRepository.save(pessoa);
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