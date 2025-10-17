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

    // ==================== BUSCA POR EMAIL ====================

    /**
     * Busca usuário por email
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Verifica se email já existe
     */
    boolean existsByEmail(String email);

    /**
     * Busca usuário por email ignorando case
     */
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Usuario> findByEmailIgnoreCase(@Param("email") String email);

    // ==================== BUSCA POR STATUS ATIVO ====================

    /**
     * Lista usuários ativos
     */
    List<Usuario> findByAtivoTrue();

    /**
     * Lista usuários inativos
     */
    List<Usuario> findByAtivoFalse();

    /**
     * Lista usuários ativos ordenados por nome
     */
    @Query("SELECT u FROM Usuario u WHERE u.ativo = true ORDER BY u.nome")
    List<Usuario> findAllAtivos();

    /**
     * Conta usuários ativos
     */
    long countByAtivoTrue();

    // ==================== BUSCA POR TIPO ====================

    /**
     * Lista usuários por tipo
     */
    List<Usuario> findByTipo(TipoUsuario tipo);

    /**
     * Conta usuários por tipo
     */
    long countByTipo(TipoUsuario tipo);

    /**
     * Lista usuários por tipo e status ativo
     */
    List<Usuario> findByTipoAndAtivoTrue(TipoUsuario tipo);

    // ==================== BUSCA POR NOME ====================

    /**
     * Busca usuários por nome contendo texto e ativos
     */
    @Query("SELECT u FROM Usuario u WHERE u.nome LIKE %:nome% AND u.ativo = true")
    List<Usuario> findByNomeContainingIgnoreCaseAndAtivoTrue(@Param("nome") String nome);

    /**
     * Busca usuários por nome contendo texto (todos)
     */
    List<Usuario> findByNomeContainingIgnoreCase(String nome);

    // ==================== BUSCA POR COMARCA ====================

    /**
     * Lista usuários por comarca
     */
    List<Usuario> findByComarca(String comarca);

    /**
     * Lista usuários ativos por comarca
     */
    List<Usuario> findByComarcaAndAtivoTrue(String comarca);

    // ==================== BUSCA POR DEPARTAMENTO ====================

    /**
     * Lista usuários por departamento
     */
    List<Usuario> findByDepartamento(String departamento);

    /**
     * Lista usuários ativos por departamento
     */
    List<Usuario> findByDepartamentoAndAtivoTrue(String departamento);

    // ==================== RESET DE SENHA ====================

    /**
     * Busca usuário por token de reset de senha
     */
    Optional<Usuario> findByPasswordResetToken(String token);

    // ==================== QUERIES COMPLEXAS ====================

    /**
     * Busca usuários com filtros múltiplos
     */
    @Query("SELECT u FROM Usuario u WHERE " +
            "(:nome IS NULL OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :nome, '%'))) AND " +
            "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
            "(:tipo IS NULL OR u.tipo = :tipo) AND " +
            "(:comarca IS NULL OR u.comarca = :comarca) AND " +
            "(:departamento IS NULL OR u.departamento = :departamento) AND " +
            "(:ativo IS NULL OR u.ativo = :ativo)")
    List<Usuario> findWithFilters(@Param("nome") String nome,
                                  @Param("email") String email,
                                  @Param("tipo") TipoUsuario tipo,
                                  @Param("comarca") String comarca,
                                  @Param("departamento") String departamento,
                                  @Param("ativo") Boolean ativo);

    /**
     * Lista administradores ativos
     */
    @Query("SELECT u FROM Usuario u WHERE u.tipo = 'ADMIN' AND u.ativo = true ORDER BY u.nome")
    List<Usuario> findAdministradoresAtivos();

    // ==================== ESTATÍSTICAS ====================

    /**
     * Lista comarcas distintas
     */
    @Query("SELECT DISTINCT u.comarca FROM Usuario u WHERE u.comarca IS NOT NULL ORDER BY u.comarca")
    List<String> findDistinctComarcas();

    /**
     * Lista departamentos distintos
     */
    @Query("SELECT DISTINCT u.departamento FROM Usuario u WHERE u.departamento IS NOT NULL ORDER BY u.departamento")
    List<String> findDistinctDepartamentos();

    // ==================== VALIDAÇÕES ====================

    /**
     * Verifica se existe outro usuário com o mesmo email (exceto o próprio)
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Usuario u WHERE u.email = :email AND u.id <> :id")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("id") Long id);

    // ==================== BUSCA PARA CONVITES ====================

    /**
     * Busca usuários que ainda não ativaram a conta
     */
    @Query("SELECT u FROM Usuario u WHERE u.ativo = false AND u.senha IS NULL")
    List<Usuario> findUsuariosPendentesAtivacao();

    /**
     * Busca usuário por email e não ativado
     */
    @Query("SELECT u FROM Usuario u WHERE u.email = :email AND u.ativo = false")
    Optional<Usuario> findByEmailAndAtivoFalse(@Param("email") String email);

}