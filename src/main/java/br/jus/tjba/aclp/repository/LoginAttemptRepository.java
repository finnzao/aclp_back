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

    @Modifying
    @Query("DELETE FROM LoginAttempt la WHERE la.attemptTime < :before")
    int deleteOldAttempts(@Param("before") LocalDateTime before);
}