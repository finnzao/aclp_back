package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.ExecucaoPenal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExecucaoPenalRepository extends JpaRepository<ExecucaoPenal, Long> {

    Optional<ExecucaoPenal> findByProcessoId(Long processoId);

    long countBySituacaoExecucao(br.jus.tjba.aclp.model.enums.SituacaoExecucao situacao);

    long countBySituacaoExecucaoAndDataTerminoPrevistaLessThanEqual(
            br.jus.tjba.aclp.model.enums.SituacaoExecucao situacao, LocalDate limite);

    boolean existsByProcessoId(Long processoId);

    boolean existsByNumeroExecucao(String numeroExecucao);

    /** Painel D-60 (fase 7): quem termina a pena até a data-limite, ainda em cumprimento. */
    @Query("SELECT e FROM ExecucaoPenal e JOIN FETCH e.processo p JOIN FETCH p.custodiado " +
            "WHERE e.situacaoExecucao = 'EM_CUMPRIMENTO' " +
            "AND e.dataTerminoPrevista <= :limite ORDER BY e.dataTerminoPrevista")
    List<ExecucaoPenal> findProximasExtincao(@Param("limite") LocalDate limite);

    @Query("SELECT e FROM ExecucaoPenal e JOIN FETCH e.processo p JOIN FETCH p.custodiado " +
            "ORDER BY e.dataTerminoPrevista")
    List<ExecucaoPenal> findAllComProcesso();
}
