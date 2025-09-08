package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /**
     * Busca código de verificação ativo por email
     * (não verificado e não expirado)
     */
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.email = :email " +
            "AND ev.verificado = false " +
            "AND ev.expiraEm > :agora " +
            "ORDER BY ev.criadoEm DESC")
    Optional<EmailVerification> findActiveByEmail(@Param("email") String email,
                                                  @Param("agora") LocalDateTime agora);

    /**
     * Busca por email e código específicos
     */
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.email = :email " +
            "AND ev.codigo = :codigo " +
            "AND ev.verificado = false " +
            "AND ev.expiraEm > :agora")
    Optional<EmailVerification> findByEmailAndCodigo(@Param("email") String email,
                                                     @Param("codigo") String codigo,
                                                     @Param("agora") LocalDateTime agora);

    /**
     * Busca códigos verificados por email
     */
    List<EmailVerification> findByEmailAndVerificadoTrueOrderByVerificadoEmDesc(String email);

    /**
     * Verifica se existe código válido para email
     */
    @Query("SELECT COUNT(ev) > 0 FROM EmailVerification ev WHERE ev.email = :email " +
            "AND ev.verificado = false " +
            "AND ev.expiraEm > :agora " +
            "AND ev.tentativas < ev.maxTentativas")
    boolean existsValidCodeByEmail(@Param("email") String email, @Param("agora") LocalDateTime agora);

    /**
     * Conta tentativas de verificação nas últimas horas por IP
     */
    @Query("SELECT COUNT(ev) FROM EmailVerification ev WHERE ev.ipSolicitacao = :ip " +
            "AND ev.criadoEm > :desde")
    long countByIpSolicitacaoAndCriadoEmAfter(@Param("ip") String ip, @Param("desde") LocalDateTime desde);

    /**
     * Conta códigos criados por email nas últimas horas
     */
    @Query("SELECT COUNT(ev) FROM EmailVerification ev WHERE ev.email = :email " +
            "AND ev.criadoEm > :desde")
    long countByEmailAndCriadoEmAfter(@Param("email") String email, @Param("desde") LocalDateTime desde);

    /**
     * Remove códigos expirados (limpeza automática)
     */
    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.expiraEm < :agora")
    int deleteExpiredCodes(@Param("agora") LocalDateTime agora);

    /**
     * Remove códigos antigos já verificados (limpeza automática)
     */
    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.verificado = true " +
            "AND ev.verificadoEm < :limite")
    int deleteOldVerifiedCodes(@Param("limite") LocalDateTime limite);

    /**
     * Busca códigos que expiraram recentemente (para notificação)
     */
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.verificado = false " +
            "AND ev.expiraEm BETWEEN :inicio AND :fim")
    List<EmailVerification> findRecentlyExpired(@Param("inicio") LocalDateTime inicio,
                                                @Param("fim") LocalDateTime fim);

    /**
     * Marca todos os códigos de um email como inválidos
     * (usado quando criar o usuário com sucesso)
     */
    @Modifying
    @Query("UPDATE EmailVerification ev SET ev.verificado = true, ev.verificadoEm = :agora " +
            "WHERE ev.email = :email AND ev.verificado = false")
    int invalidateAllByEmail(@Param("email") String email, @Param("agora") LocalDateTime agora);

    /**
     * Busca estatísticas de verificação por período
     */
    @Query("SELECT " +
            "COUNT(ev) as total, " +
            "SUM(CASE WHEN ev.verificado = true THEN 1 ELSE 0 END) as verificados, " +
            "SUM(CASE WHEN ev.expiraEm < :agora AND ev.verificado = false THEN 1 ELSE 0 END) as expirados " +
            "FROM EmailVerification ev WHERE ev.criadoEm BETWEEN :inicio AND :fim")
    Object[] getStatisticsByPeriod(@Param("inicio") LocalDateTime inicio,
                                   @Param("fim") LocalDateTime fim,
                                   @Param("agora") LocalDateTime agora);

    /**
     * Busca último código verificado por email (para auditoria)
     */
    @Query("SELECT ev FROM EmailVerification ev WHERE ev.email = :email " +
            "AND ev.verificado = true " +
            "ORDER BY ev.verificadoEm DESC LIMIT 1")
    Optional<EmailVerification> findLastVerifiedByEmail(@Param("email") String email);
}