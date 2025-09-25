package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUsuarioEmail(String email);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.usuario.email = :email")
    void deleteByUsuarioEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.usuario.id = :userId AND rt.revoked = false AND rt.expiryDate > :now")
    List<RefreshToken> findActiveTokensByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.usuario.id = :userId")
    void revokeAllUserTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}