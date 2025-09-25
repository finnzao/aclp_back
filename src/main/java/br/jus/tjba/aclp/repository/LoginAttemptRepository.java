package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.email = :email AND la.success = false AND la.attemptTime > :since")
    long countFailedAttemptsByEmail(@Param("email") String email, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.ipAddress = :ip AND la.attemptTime > :since")
    long countRecentAttemptsByIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    List<LoginAttempt> findByEmailOrderByAttemptTimeDesc(String email);

    List<LoginAttempt> findByIpAddressOrderByAttemptTimeDesc(String ipAddress);

    @Query("SELECT la FROM LoginAttempt la WHERE la.attemptTime BETWEEN :start AND :end ORDER BY la.attemptTime DESC")
    List<LoginAttempt> findByPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT la FROM LoginAttempt la WHERE la.suspicious = true AND la.attemptTime > :since")
    List<LoginAttempt> findSuspiciousAttempts(@Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM LoginAttempt la WHERE la.attemptTime < :before")
    int deleteOldAttempts(@Param("before") LocalDateTime before);
}