package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Providencia;
import br.jus.tjba.aclp.model.enums.TipoProvidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ProvidenciaRepository extends JpaRepository<Providencia, Long> {

    List<Providencia> findByExecucaoPenalIdOrderByConcluidaEmAscPrazoAscCriadoEmDesc(Long execucaoId);

    boolean existsByExecucaoPenalIdAndTipoAndConcluidaEmIsNull(Long execucaoId, TipoProvidencia tipo);

    long countByConcluidaEmIsNullAndPrazoBefore(LocalDate hoje);

    @Query("SELECT p FROM Providencia p JOIN FETCH p.execucaoPenal e JOIN FETCH e.processo pr JOIN FETCH pr.custodiado " +
            "WHERE p.concluidaEm IS NULL " +
            "ORDER BY CASE WHEN p.prazo IS NULL THEN 1 ELSE 0 END, p.prazo, p.prioritaria DESC, p.criadoEm")
    List<Providencia> findPendentes();

    @Query("SELECT p.execucaoPenal.id, MAX(COALESCE(p.concluidaEm, CAST(p.criadoEm AS date))) " +
            "FROM Providencia p WHERE p.execucaoPenal.id IN :ids GROUP BY p.execucaoPenal.id")
    List<Object[]> ultimaMovimentacaoPorExecucao(@Param("ids") Collection<Long> ids);
}
