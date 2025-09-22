package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.UserInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserInviteRepository extends JpaRepository<UserInvite, Long> {

    /**
     * Busca convite por token
     */
    Optional<UserInvite> findByToken(String token);

    /**
     * Busca convites por email
     */
    List<UserInvite> findByEmail(String email);

    /**
     * Busca convites por status
     */
    List<UserInvite> findByStatus(String status);

    /**
     * Verifica se existe convite pendente para email
     */
    @Query("SELECT COUNT(i) > 0 FROM UserInvite i WHERE i.email = :email AND i.status = 'PENDING' AND i.expiraEm > :now")
    boolean existsPendingByEmail(@Param("email") String email, @Param("now") LocalDateTime now);

    default boolean existsPendingByEmail(String email) {
        return existsPendingByEmail(email, LocalDateTime.now());
    }

    /**
     * Busca convites expirados
     */
    @Query("SELECT i FROM UserInvite i WHERE i.status = 'PENDING' AND i.expiraEm < :now")
    List<UserInvite> findExpiredInvites(@Param("now") LocalDateTime now);

    /**
     * Busca convites criados por um admin específico
     */
    @Query("SELECT i FROM UserInvite i WHERE i.criadoPor.id = :adminId ORDER BY i.criadoEm DESC")
    List<UserInvite> findByCreatedBy(@Param("adminId") Long adminId);

    /**
     * Busca convites por período
     */
    @Query("SELECT i FROM UserInvite i WHERE i.criadoEm BETWEEN :inicio AND :fim ORDER BY i.criadoEm DESC")
    List<UserInvite> findByPeriod(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /**
     * Conta convites por status
     */
    @Query("SELECT i.status, COUNT(i) FROM UserInvite i GROUP BY i.status")
    List<Object[]> countByStatus();

    /**
     * Busca convites pendentes que expiram em breve
     */
    @Query("SELECT i FROM UserInvite i WHERE i.status = 'PENDING' AND i.expiraEm BETWEEN :now AND :limite")
    List<UserInvite> findExpiringInvites(@Param("now") LocalDateTime now, @Param("limite") LocalDateTime limite);

    /**
     * Conta convites aceitos por período
     */
    @Query("SELECT COUNT(i) FROM UserInvite i WHERE i.status = 'ACCEPTED' AND i.aceitoEm BETWEEN :inicio AND :fim")
    long countAcceptedByPeriod(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}