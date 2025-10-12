package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.PreCadastro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para gerenciar pré-cadastros
 */
@Repository
public interface PreCadastroRepository extends JpaRepository<PreCadastro, Long> {

    /**
     * Busca pré-cadastro pelo token de verificação
     */
    Optional<PreCadastro> findByTokenVerificacao(String tokenVerificacao);

    /**
     * Busca pré-cadastro por email
     */
    Optional<PreCadastro> findByEmail(String email);

    /**
     * Verifica se existe pré-cadastro pendente para email
     */
    boolean existsByEmailAndVerificadoFalse(String email);

    /**
     * Busca pré-cadastros expirados e não verificados para limpeza
     */
    @Query("SELECT p FROM PreCadastro p WHERE p.verificado = false AND p.expiraEm < :dataAtual")
    List<PreCadastro> findExpiradosNaoVerificados(@Param("dataAtual") LocalDateTime dataAtual);

    /**
     * Conta pré-cadastros aguardando verificação
     */
    long countByVerificadoFalse();

    /**
     * Busca pré-cadastros por token do convite
     */
    List<PreCadastro> findByTokenConvite(String tokenConvite);

    /**
     * Remove pré-cadastros expirados (job de limpeza)
     */
    @Query("DELETE FROM PreCadastro p WHERE p.verificado = false AND p.expiraEm < :dataAtual")
    void deleteExpirados(@Param("dataAtual") LocalDateTime dataAtual);
}