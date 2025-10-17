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
     * Busca convite por email e status (apenas convites com email específico)
     */
    Optional<Convite> findByEmailAndStatus(String email, StatusConvite status);

    /**
     * Lista convites por status
     */
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

    /**
     * Verifica se email já foi usado em algum convite ativado
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Convite c " +
            "WHERE c.email = :email " +
            "AND c.status = 'ATIVADO'")
    boolean existsEmailJaUtilizado(@Param("email") String email);

    /**
     * Lista convites criados por um usuário
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.criadoPor.id = :usuarioId " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findByCriadoPorId(@Param("usuarioId") Long usuarioId);

    /**
     * Lista convites genéricos (sem email específico)
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.email IS NULL " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findConvitesGenericos();

    /**
     * Lista convites com email específico
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.email IS NOT NULL " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findConvitesEspecificos();

    /**
     * Lista convites reutilizáveis (não existe mais - uso único)
     * @deprecated Todos os convites são de uso único
     */
    @Deprecated
    @Query("SELECT c FROM Convite c " +
            "WHERE c.quantidadeUsos = 1 " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findConvitesReutilizaveis();

    /**
     * Lista convites genéricos válidos (não usados e não expirados)
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.email IS NULL " +
            "AND c.status = 'PENDENTE' " +
            "AND c.expiraEm > :dataAtual " +
            "AND c.usosRealizados = 0 " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findConvitesGenericosValidos(@Param("dataAtual") LocalDateTime dataAtual);

    /**
     * Lista convites expirados que ainda estão com status PENDENTE
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.status = 'PENDENTE' " +
            "AND c.expiraEm < :dataAtual")
    List<Convite> findConvitesExpirados(@Param("dataAtual") LocalDateTime dataAtual);

    /**
     * Lista convites que foram usados (não existe mais esgotamento)
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.status = 'PENDENTE' " +
            "AND c.usosRealizados >= 1")
    List<Convite> findConvitesEsgotados();

    /**
     * Conta convites pendentes
     */
    long countByStatus(StatusConvite status);

    /**
     * Conta convites genéricos ativos
     */
    @Query("SELECT COUNT(c) FROM Convite c " +
            "WHERE c.email IS NULL " +
            "AND c.status = 'PENDENTE' " +
            "AND c.expiraEm > :dataAtual")
    long countConvitesGenericosAtivos(@Param("dataAtual") LocalDateTime dataAtual);

    /**
     * Lista todos os convites ordenados por data de criação
     */
    @Query("SELECT c FROM Convite c ORDER BY c.criadoEm DESC")
    List<Convite> findAllOrderByCreatedDesc();

    /**
     * Busca convites por comarca
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.comarca = :comarca " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findByComarca(@Param("comarca") String comarca);

    /**
     * Busca convites por departamento
     */
    @Query("SELECT c FROM Convite c " +
            "WHERE c.departamento = :departamento " +
            "ORDER BY c.criadoEm DESC")
    List<Convite> findByDepartamento(@Param("departamento") String departamento);
}