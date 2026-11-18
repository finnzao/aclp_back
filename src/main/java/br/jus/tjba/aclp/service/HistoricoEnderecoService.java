package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricoEnderecoService {

    private final HistoricoEnderecoRepository historicoEnderecoRepository;
    private final PessoaMonitoradaRepository custodiadoRepository;

    @Transactional
    public HistoricoEnderecoDTO registrarEndereco(Long pessoaId, HistoricoEnderecoDTO dto, String por) {
        PessoaMonitorada pessoa = custodiadoRepository.findById(pessoaId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Pessoa não encontrada: " + pessoaId));
        for (String campo : new String[]{dto.getCep(), dto.getLogradouro(), dto.getBairro(), dto.getCidade(), dto.getEstado()}) {
            if (campo == null || campo.trim().isEmpty())
                throw new IllegalArgumentException("CEP, logradouro, bairro, cidade e UF são obrigatórios");
        }
        historicoEnderecoRepository.desativarTodosEnderecosPorCustodiado(pessoaId);
        HistoricoEndereco salvo = historicoEnderecoRepository.save(HistoricoEndereco.builder()
                .custodiado(pessoa).cep(dto.getCep()).logradouro(dto.getLogradouro().trim())
                .numero(dto.getNumero()).complemento(dto.getComplemento()).bairro(dto.getBairro().trim())
                .cidade(dto.getCidade().trim()).estado(dto.getEstado().trim().toUpperCase())
                .dataInicio(dto.getDataInicio() != null ? dto.getDataInicio() : LocalDate.now())
                .ativo(Boolean.TRUE)
                .motivoAlteracao(dto.getMotivoAlteracao() != null ? dto.getMotivoAlteracao() : "Endereço informado pela ficha")
                .validadoPor(por).build());
        historicoEnderecoRepository.desativarOutrosEnderecosAtivos(pessoaId, salvo.getId());
        return convertToDTO(salvo);
    }

    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarHistoricoPorCustodiado(Long pessoaId) {
        log.info("Buscando histórico de endereços - Pessoa monitorada ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        List<HistoricoEndereco> historicos = historicoEnderecoRepository.findByCustodiadoIdOrderByDataInicioDesc(pessoaId);

        return historicos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<HistoricoEnderecoDTO> buscarEnderecoAtivo(Long pessoaId) {
        log.info("Buscando endereço ativo - Pessoa monitorada ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(pessoaId)
                .map(this::convertToDTO);
    }

    private HistoricoEnderecoDTO convertToDTO(HistoricoEndereco endereco) {
        return HistoricoEnderecoDTO.builder()
                .id(endereco.getId())
                .pessoaId(endereco.getCustodiado().getId())
                .cep(endereco.getCep())
                .logradouro(endereco.getLogradouro())
                .numero(endereco.getNumero())
                .complemento(endereco.getComplemento())
                .bairro(endereco.getBairro())
                .cidade(endereco.getCidade())
                .estado(endereco.getEstado())
                .dataInicio(endereco.getDataInicio())
                .dataFim(endereco.getDataFim())
                .motivoAlteracao(endereco.getMotivoAlteracao())
                .validadoPor(endereco.getValidadoPor())
                .historicoComparecimentoId(endereco.getHistoricoComparecimento() != null ?
                        endereco.getHistoricoComparecimento().getId() : null)
                .criadoEm(endereco.getCriadoEm())
                .atualizadoEm(endereco.getAtualizadoEm())
                .version(endereco.getVersion())
                // Campos calculados
                .enderecoCompleto(endereco.getEnderecoCompleto())
                .enderecoResumido(endereco.getEnderecoResumido())
                .nomeEstado(endereco.getNomeEstado())
                .regiaoEstado(endereco.getRegiaoEstado())
                .periodoResidencia(endereco.getPeriodoResidencia())
                .diasResidencia(endereco.getDiasResidencia())
                .enderecoAtivo(endereco.isEnderecoAtivo())
                .build();
    }

}