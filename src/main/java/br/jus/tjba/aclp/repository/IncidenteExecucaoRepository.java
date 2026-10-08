package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.IncidenteExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IncidenteExecucaoRepository extends JpaRepository<IncidenteExecucao, Long> {

    List<IncidenteExecucao> findByExecucaoPenalIdOrderByDataOcorrenciaDesc(Long execucaoPenalId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT i.execucaoPenal.id, MAX(COALESCE(i.dataDecisao, i.dataOcorrencia)) " +
            "FROM IncidenteExecucao i WHERE i.execucaoPenal.id IN :ids GROUP BY i.execucaoPenal.id")
    List<Object[]> ultimaMovimentacaoPorExecucao(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
}
