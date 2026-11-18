package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.PreCadastro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PreCadastroRepository extends JpaRepository<PreCadastro, Long> {

    Optional<PreCadastro> findByTokenVerificacao(String tokenVerificacao);

    Optional<PreCadastro> findByEmail(String email);

    boolean existsByEmailAndVerificadoFalse(String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM PreCadastro p WHERE p.verificado = false AND p.expiraEm < :dataAtual")
    void deleteExpirados(@Param("dataAtual") LocalDateTime dataAtual);
}