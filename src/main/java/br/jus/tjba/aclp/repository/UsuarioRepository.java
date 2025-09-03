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

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByTipo(TipoUsuario tipo);

    List<Usuario> findByAtivoTrue();

    @Query("SELECT u FROM Usuario u WHERE u.nome LIKE %:nome% AND u.ativo = true")
    List<Usuario> findByNomeContainingIgnoreCaseAndAtivoTrue(@Param("nome") String nome);

    boolean existsByEmail(String email);

    long countByTipo(TipoUsuario tipo);
}