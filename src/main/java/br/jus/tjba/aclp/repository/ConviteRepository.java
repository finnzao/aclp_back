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

/**
 * Repository para gerenciar convites
 */
@Repository
public interface ConviteRepository extends JpaRepository<Convite, Long> {

    /**
     * Busca convite pelo token único
     */
    Optional<Convite> findByToken(String token);

    /**
     * Busca convite por email e status
     */
    Optional<Convite> findByEmailAndStatus(String email, StatusConvite status);

    /**
     * Lista convites por status
     */
    List<Convite> findByStatus(StatusConvite status);

    /**
     * Verifica se existe convite pendente para email
     */
    boolean existsByEmailAndStatus(String email, StatusConvite status);

    /**
     * Lista convites criados por um usuário
     */
    @Query("SELECT c FROM Convite c WHERE c.criadoPor.id = :usuarioId ORDER BY c.criadoEm DESC")
    List<Convite> findByCriadoPorId(@Param("usuarioId") Long usuarioId);

    /**
     * Lista convites expirados que ainda estão com status PENDENTE
     */
    @Query("SELECT c FROM Convite c WHERE c.status = 'PENDENTE' AND c.expiraEm < :dataAtual")
    List<Convite> findConvitesExpirados(@Param("dataAtual") LocalDateTime dataAtual);

    /**
     * Conta convites pendentes
     */
    long countByStatus(StatusConvite status);

    /**
     * Lista todos os convites ordenados por data de criação
     */
    @Query("SELECT c FROM Convite c ORDER BY c.criadoEm DESC")
    List<Convite> findAllOrderByCreatedDesc();
}