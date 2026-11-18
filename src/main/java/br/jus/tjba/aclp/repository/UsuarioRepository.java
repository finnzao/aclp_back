package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     Busca usuário por email.
     */
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Usuario> findByEmail(@Param("email") String email);

    /**
     * Verifica se email já existe. Mesma normalização de findByEmail — se divergirem,
     * a checagem de duplicidade deixa passar um par como "a@x" / "A@x" e a busca
     * subsequente encontra duas linhas para o mesmo login.
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    List<Usuario> findByAtivoTrue();

    @Query("SELECT u FROM Usuario u WHERE u.ativo = true ORDER BY u.nome")
    List<Usuario> findAllAtivos();

    List<Usuario> findByTipo(TipoUsuario tipo);

    long countByTipo(TipoUsuario tipo);

    List<Usuario> findByComarca(String comarca);

    List<Usuario> findByDepartamento(String departamento);

    Optional<Usuario> findByPasswordResetToken(String token);

}