package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Convite;
import br.jus.tjba.aclp.model.enums.StatusConvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConviteRepository extends JpaRepository<Convite, Long> {

    Optional<Convite> findByToken(String token);

    List<Convite> findByStatus(StatusConvite status);

    /**
     * Verifica se existe convite pendente para email específico
     * Ignora convites genéricos (email NULL)
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Convite c " +
            "WHERE c.email = :email " +
            "AND c.status = :status " +
            "AND c.email IS NOT NULL")
    boolean existsByEmailAndStatus(@Param("email") String email,
                                   @Param("status") StatusConvite status);

    @Query("SELECT c FROM Convite c " +
            "WHERE c.criadoPor.id = :usuarioId " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findByCriadoPorId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT c FROM Convite c " +
            "WHERE c.status = 'PENDENTE' " +
            "AND c.expiraEm < :dataAtual")
    List<Convite> findConvitesExpirados(@Param("dataAtual") LocalDateTime dataAtual);

    long countByStatus(StatusConvite status);

    @Query("SELECT c FROM Convite c " +
            "WHERE c.comarca = :comarca " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findByComarca(@Param("comarca") String comarca);

    @Query("SELECT c FROM Convite c " +
            "WHERE c.departamento = :departamento " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findByDepartamento(@Param("departamento") String departamento);
}