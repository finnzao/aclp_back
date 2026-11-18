package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.PessoaMonitorada;
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

    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findByCustodiadoIdOrderByDataInicioDesc(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca o endereço ativo atual de um custodiado
     * CORREÇÃO: Garante que retorna apenas um endereço ativo (o mais recente)
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.custodiado.id = :custodiadoId AND h.ativo = true " +
            "ORDER BY h.dataInicio DESC, h.id DESC LIMIT 1")
    Optional<HistoricoEndereco> findEnderecoAtivoPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    @Modifying
    @Query("UPDATE HistoricoEndereco h SET h.ativo = false, h.atualizadoEm = CURRENT_TIMESTAMP " +
            "WHERE h.custodiado.id = :custodiadoId AND h.ativo = true")
    int desativarTodosEnderecosPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    @Modifying
    @Query("UPDATE HistoricoEndereco h SET h.ativo = false, h.atualizadoEm = CURRENT_TIMESTAMP " +
            "WHERE h.custodiado.id = :custodiadoId AND h.ativo = true AND h.id != :enderecoId")
    int desativarOutrosEnderecosAtivos(@Param("custodiadoId") Long custodiadoId, @Param("enderecoId") Long enderecoId);

    @Query("SELECT h FROM HistoricoEndereco h WHERE h.ativo = true")
    List<HistoricoEndereco> findAllEnderecosAtivos();

    @Query("SELECT COUNT(DISTINCT c) FROM PessoaMonitorada c WHERE c.id NOT IN " +
            "(SELECT h.custodiado.id FROM HistoricoEndereco h WHERE h.ativo = true)")
    long countPessoasMonitoradasSemEnderecoAtivo();
}