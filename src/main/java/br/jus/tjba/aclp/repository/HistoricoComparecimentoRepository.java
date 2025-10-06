package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HistoricoComparecimentoRepository extends JpaRepository<HistoricoComparecimento, Long> {

    List<HistoricoComparecimento> findByCustodiadoIdOrderByDataComparecimentoDesc(Long custodiadoId);

    List<HistoricoComparecimento> findByDataComparecimentoBetween(LocalDate inicio, LocalDate fim);

    List<HistoricoComparecimento> findByDataComparecimento(LocalDate data);

    List<HistoricoComparecimento> findByCustodiadoIdAndMudancaEnderecoTrue(Long custodiadoId);

    List<HistoricoComparecimento> findByCustodiadoIdAndDataComparecimento(Long custodiadoId, LocalDate data);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM HistoricoComparecimento h " +
            "WHERE h.custodiado.id = :custodiadoId AND h.tipoValidacao = 'CADASTRO_INICIAL'")
    boolean existsCadastroInicialPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    // NOVOS MÉTODOS ADICIONADOS

    Page<HistoricoComparecimento> findAllByOrderByDataComparecimentoDesc(Pageable pageable);

    @Query("SELECT COUNT(h) FROM HistoricoComparecimento h")
    long countTotal();

    @Query("SELECT h FROM HistoricoComparecimento h WHERE " +
            "(:dataInicio IS NULL OR h.dataComparecimento >= :dataInicio) AND " +
            "(:dataFim IS NULL OR h.dataComparecimento <= :dataFim) AND " +
            "(:tipoValidacao IS NULL OR CAST(h.tipoValidacao AS string) = :tipoValidacao) " +
            "ORDER BY h.dataComparecimento DESC")
    Page<HistoricoComparecimento> findComFiltros(
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            @Param("tipoValidacao") String tipoValidacao,
            Pageable pageable);

    @Query("SELECT h FROM HistoricoComparecimento h WHERE " +
            "CAST(h.tipoValidacao AS string) = :tipoValidacao " +
            "ORDER BY h.dataComparecimento DESC")
    Page<HistoricoComparecimento> findByTipoValidacao(
            @Param("tipoValidacao") String tipoValidacao,
            Pageable pageable);

    @Query("SELECT COUNT(h) FROM HistoricoComparecimento h WHERE " +
            "CAST(h.tipoValidacao AS string) = :tipoValidacao")
    long countByTipoValidacao(@Param("tipoValidacao") String tipoValidacao);

    @Query("SELECT COUNT(h) FROM HistoricoComparecimento h WHERE h.mudancaEndereco = true")
    long countComMudancaEndereco();
}