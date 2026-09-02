package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.IncidenteExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidenteExecucaoRepository extends JpaRepository<IncidenteExecucao, Long> {

    List<IncidenteExecucao> findByExecucaoPenalIdOrderByDataOcorrenciaDesc(Long execucaoPenalId);
}
