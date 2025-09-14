package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
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
    private final CustodiadoRepository custodiadoRepository;

    /**
     * Busca histórico completo de endereços de uma pessoa
     */
    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarHistoricoPorCustodiado(Long pessoaId) {
        log.info("Buscando histórico de endereços - Custodiado ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        List<HistoricoEndereco> historicos = historicoEnderecoRepository.findByCustodiadoIdOrderByDataInicioDesc(pessoaId);

        return historicos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca endereço ativo atual de uma pessoa
     */
    @Transactional(readOnly = true)
    public Optional<HistoricoEnderecoDTO> buscarEnderecoAtivo(Long pessoaId) {
        log.info("Buscando endereço ativo - Custodiado ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        return historicoEnderecoRepository.findEnderecoAtivoPorCustodiado(pessoaId)
                .map(this::convertToDTO);
    }

    /**
     * Busca endereços históricos (finalizados) de uma pessoa
     */
    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarEnderecosHistoricos(Long pessoaId) {
        log.info("Buscando endereços históricos - Custodiado ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        List<HistoricoEndereco> historicos = historicoEnderecoRepository.findEnderecosHistoricosPorCustodiado(pessoaId);

        return historicos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca endereços por período específico
     */
    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarEnderecosPorPeriodo(Long pessoaId, LocalDate inicio, LocalDate fim) {
        log.info("Buscando endereços por período - Custodiado ID: {}, Período: {} a {}", pessoaId, inicio, fim);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Data de início e fim são obrigatórias");
        }

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Data de início não pode ser posterior à data de fim");
        }

        List<HistoricoEndereco> historicos = historicoEnderecoRepository.findEnderecosPorPeriodo(pessoaId, inicio, fim);

        return historicos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca pessoas por cidade
     */
    @Transactional(readOnly = true)
    public List<Custodiado> buscarCustodiadosPorCidade(String cidade) {
        log.info("Buscando pessoas por cidade: {}", cidade);

        if (cidade == null || cidade.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da cidade é obrigatório");
        }

        return historicoEnderecoRepository.findCustodiadosPorCidade(cidade.trim());
    }

    /**
     * Busca pessoas por estado
     */
    @Transactional(readOnly = true)
    public List<Custodiado> buscarCustodiadosPorEstado(String estado) {
        log.info("Buscando pessoas por estado: {}", estado);

        if (estado == null || estado.trim().isEmpty()) {
            throw new IllegalArgumentException("Sigla do estado é obrigatória");
        }

        return historicoEnderecoRepository.findCustodiadosPorEstado(estado.trim().toUpperCase());
    }

    /**
     * Busca mudanças de endereço em um período
     */
    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarMudancasPorPeriodo(LocalDate inicio, LocalDate fim) {
        log.info("Buscando mudanças de endereço por período: {} a {}", inicio, fim);

        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Data de início e fim são obrigatórias");
        }

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Data de início não pode ser posterior à data de fim");
        }

        List<HistoricoEndereco> mudancas = historicoEnderecoRepository.findMudancasEnderecoPorPeriodo(inicio, fim);

        return mudancas.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Conta quantos endereços uma pessoa já teve
     */
    @Transactional(readOnly = true)
    public long contarEnderecosPorCustodiado(Long pessoaId) {
        log.info("Contando endereços - Custodiado ID: {}", pessoaId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        Custodiado pessoa = custodiadoRepository.findById(pessoaId)
                .orElseThrow(() -> new EntityNotFoundException("Custodiado não encontrada com ID: " + pessoaId));

        return historicoEnderecoRepository.countByCustodiado(pessoa);
    }

    /**
     * Busca estatísticas de endereços por cidade
     */
    @Transactional(readOnly = true)
    public List<EstatisticasEndereco> buscarEstatisticasPorCidade() {
        log.info("Buscando estatísticas de endereços por cidade");

        List<Object[]> resultados = historicoEnderecoRepository.findEstatisticasMudancasPorCustodiado();

        // Converter resultados em estatísticas por cidade
        // Esta implementação pode ser expandida conforme necessário
        return resultados.stream()
                .map(row -> EstatisticasEndereco.builder()
                        .pessoaId((Long) row[0])
                        .totalMudancas((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Busca endereços que estavam ativos em uma data específica
     */
    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarEnderecosAtivosPorData(LocalDate data) {
        log.info("Buscando endereços ativos em: {}", data);

        if (data == null) {
            throw new IllegalArgumentException("Data é obrigatória");
        }

        List<HistoricoEndereco> enderecos = historicoEnderecoRepository.findEnderecosAtivosPorData(data);

        return enderecos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca endereços por motivo de alteração
     */
    @Transactional(readOnly = true)
    public List<HistoricoEnderecoDTO> buscarPorMotivoAlteracao(String motivo) {
        log.info("Buscando endereços por motivo: {}", motivo);

        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("Motivo de alteração é obrigatório");
        }

        List<HistoricoEndereco> enderecos = historicoEnderecoRepository.findByMotivoAlteracaoContaining(motivo.trim());

        return enderecos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Verifica se existe sobreposição de endereços
     */
    @Transactional(readOnly = true)
    public boolean verificarSobreposicaoEndereco(Long pessoaId, Long enderecoId, LocalDate dataInicio, LocalDate dataFim) {
        log.info("Verificando sobreposição de endereço - Custodiado ID: {}, Endereço ID: {}", pessoaId, enderecoId);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        if (dataInicio == null) {
            throw new IllegalArgumentException("Data de início é obrigatória");
        }

        return historicoEnderecoRepository.existeSobreposicaoEndereco(pessoaId, enderecoId, dataInicio, dataFim);
    }

    /**
     * Busca último endereço anterior a uma data
     */
    @Transactional(readOnly = true)
    public Optional<HistoricoEnderecoDTO> buscarUltimoEnderecoAnterior(Long pessoaId, LocalDate data) {
        log.info("Buscando último endereço anterior - Custodiado ID: {}, Data: {}", pessoaId, data);

        if (pessoaId == null || pessoaId <= 0) {
            throw new IllegalArgumentException("ID da pessoa deve ser um número positivo");
        }

        if (data == null) {
            throw new IllegalArgumentException("Data é obrigatória");
        }

        return historicoEnderecoRepository.findUltimoEnderecoAnteriorData(pessoaId, data)
                .map(this::convertToDTO);
    }

    // ========== MÉTODOS PRIVADOS ==========

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

    /**
     * DTO para estatísticas de endereços
     */
    @lombok.Data
    @lombok.Builder
    public static class EstatisticasEndereco {
        private Long pessoaId;
        private String cidade;
        private String estado;
        private String regiao;
        private Long totalMudancas;
        private Long totalCustodiados;
        private Double mediaDiasResidencia;
    }
}