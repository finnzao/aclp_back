package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricoEnderecoRepository extends JpaRepository<HistoricoEndereco, Long> {

    /**
     * Busca histórico de endereços de um custodiado ordenado por data de início (mais recente primeiro)
     */
    List<HistoricoEndereco> findByCustodiadoOrderByDataInicioDesc(Custodiado custodiado);

    /**
     * Busca histórico de endereços de um custodiado por ID ordenado por data de início
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findByCustodiadoIdOrderByDataInicioDesc(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca o endereço ativo atual de um custodiado
     * CORREÇÃO: Garante que retorna apenas um endereço ativo (o mais recente)
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId AND h.ativo = true " +
            "ORDER BY h.dataInicio DESC, h.id DESC LIMIT 1")
    Optional<HistoricoEndereco> findEnderecoAtivoPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * NOVO: Desativa todos os endereços ativos de um custodiado
     */
    @Modifying
    @Query("UPDATE HistoricoEndereco h SET h.ativo = false, h.atualizadoEm = CURRENT_TIMESTAMP " +
            "WHERE h.custodiado.id = :custodiadoId AND h.ativo = true")
    int desativarTodosEnderecosPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * NOVO: Desativa endereços ativos de um custodiado exceto um específico
     */
    @Modifying
    @Query("UPDATE HistoricoEndereco h SET h.ativo = false, h.atualizadoEm = CURRENT_TIMESTAMP " +
            "WHERE h.custodiado.id = :custodiadoId AND h.ativo = true AND h.id != :enderecoId")
    int desativarOutrosEnderecosAtivos(@Param("custodiadoId") Long custodiadoId, @Param("enderecoId") Long enderecoId);

    /**
     * NOVO: Busca todos os endereços ativos duplicados (para diagnóstico)
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id IN " +
            "(SELECT h2.custodiado.id FROM HistoricoEndereco h2 WHERE h2.ativo = true " +
            "GROUP BY h2.custodiado.id HAVING COUNT(h2.id) > 1) " +
            "AND h.ativo = true ORDER BY h.custodiado.id, h.dataInicio DESC")
    List<HistoricoEndereco> findEnderecosDuplicados();

    /**
     * NOVO: Conta endereços ativos por custodiado (para validação)
     */
    @Query("SELECT COUNT(h) FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId AND h.ativo = true")
    long countEnderecosAtivosPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca endereços históricos de um custodiado (inativos)
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId AND h.ativo = false ORDER BY h.dataFim DESC")
    List<HistoricoEndereco> findEnderecosHistoricosPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca endereços por período específico
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId " +
            "AND h.dataInicio <= :dataFim " +
            "AND (h.dataFim IS NULL OR h.dataFim >= :dataInicio) " +
            "ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findEnderecosPorPeriodo(@Param("custodiadoId") Long custodiadoId,
                                                    @Param("dataInicio") LocalDate dataInicio,
                                                    @Param("dataFim") LocalDate dataFim);

    /**
     * Busca endereços por CEP
     */
    List<HistoricoEndereco> findByCep(String cep);

    /**
     * Busca endereços por cidade
     */
    List<HistoricoEndereco> findByCidade(String cidade);

    /**
     * Busca endereços por estado
     */
    List<HistoricoEndereco> findByEstado(String estado);

    /**
     * Conta quantos endereços um custodiado já teve
     */
    long countByCustodiado(Custodiado custodiado);

    /**
     * Busca endereços que estavam ativos em uma data específica
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.dataInicio <= :data " +
            "AND (h.dataFim IS NULL OR h.dataFim > :data) AND h.ativo = true")
    List<HistoricoEndereco> findEnderecosAtivosPorData(@Param("data") LocalDate data);

    /**
     * Busca mudanças de endereço em um período
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.dataInicio BETWEEN :inicio AND :fim " +
            "ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findMudancasEnderecoPorPeriodo(@Param("inicio") LocalDate inicio,
                                                           @Param("fim") LocalDate fim);

    /**
     * Busca endereços por comparecimento específico
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.historicoComparecimento.id = :comparecimentoId")
    List<HistoricoEndereco> findByHistoricoComparecimentoId(@Param("comparecimentoId") Long comparecimentoId);

    /**
     * Busca custodiados que moram/moraram em uma cidade específica
     */
    @Query("SELECT DISTINCT h.custodiado FROM HistoricoEndereco h WHERE h.cidade = :cidade")
    List<Custodiado> findCustodiadosPorCidade(@Param("cidade") String cidade);

    /**
     * Busca custodiados que moram/moraram em um estado específico
     */
    @Query("SELECT DISTINCT h.custodiado FROM HistoricoEndereco h WHERE h.estado = :estado")
    List<Custodiado> findCustodiadosPorEstado(@Param("estado") String estado);

    /**
     * Busca endereços com motivação específica
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.motivoAlteracao LIKE %:motivo% ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findByMotivoAlteracaoContaining(@Param("motivo") String motivo);

    /**
     * Busca estatísticas de mudanças por custodiado
     */
    @Query("SELECT h.custodiado.id, COUNT(h) as totalMudancas FROM HistoricoEndereco h " +
            "WHERE h.ativo = false GROUP BY h.custodiado.id ORDER BY totalMudancas DESC")
    List<Object[]> findEstatisticasMudancasPorCustodiado();

    /**
     * Busca endereços que duraram menos que X dias
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.dataFim IS NOT NULL " +
            "AND FUNCTION('DATE_PART', 'day', h.dataFim - h.dataInicio) < :dias")
    List<HistoricoEndereco> findEnderecosComPoucaDuracao(@Param("dias") long dias);

    /**
     * Verifica se existe sobreposição de endereços para um custodiado
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId " +
            "AND h.id != :enderecoId " +
            "AND h.dataInicio <= :dataFim " +
            "AND (h.dataFim IS NULL OR h.dataFim >= :dataInicio)")
    boolean existeSobreposicaoEndereco(@Param("custodiadoId") Long custodiadoId,
                                       @Param("enderecoId") Long enderecoId,
                                       @Param("dataInicio") LocalDate dataInicio,
                                       @Param("dataFim") LocalDate dataFim);

    /**
     * Busca o último endereço anterior a uma data específica
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId " +
            "AND h.dataInicio <= :data ORDER BY h.dataInicio DESC LIMIT 1")
    Optional<HistoricoEndereco> findUltimoEnderecoAnteriorData(@Param("custodiadoId") Long custodiadoId,
                                                               @Param("data") LocalDate data);

    /**
     * Busca todos os endereços ativos
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.ativo = true")
    List<HistoricoEndereco> findAllEnderecosAtivos();

    /**
     * Conta custodiados sem endereço ativo
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Custodiado c WHERE c.id NOT IN " +
            "(SELECT h.custodiado.id FROM HistoricoEndereco h WHERE h.ativo = true)")
    long countCustodiadosSemEnderecoAtivo();
}