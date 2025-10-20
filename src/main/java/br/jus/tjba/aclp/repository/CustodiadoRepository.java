package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustodiadoRepository extends JpaRepository<Custodiado, Long> {

    // Queries otimizadas com JOIN FETCH - evitam N+1 queries
    // Sempre usar SELECT DISTINCT quando fazer JOIN FETCH em coleções

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.id = :id AND (he.ativo = true OR he IS NULL)")
    Optional<Custodiado> findByIdWithEnderecoAtivo(@Param("id") Long id);

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL) " +
            "ORDER BY c.nome")
    List<Custodiado> findAllActiveWithEnderecos();

    // Alias para compatibilidade com codigo existente
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL) " +
            "ORDER BY c.nome")
    List<Custodiado> findAllWithEnderecosAtivos();

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.processo = :processo AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByProcessoWithEnderecos(@Param("processo") String processo);

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.status = :status AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByStatusWithEnderecos(@Param("status") StatusComparecimento status);

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.proximoComparecimento = CURRENT_DATE AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findComparecimentosHojeWithEnderecos();

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) " +
            "AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findInadimplentesWithEnderecos();

    // Versao com dois parametros para busca por nome OU processo
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "OR c.processo LIKE CONCAT('%', :processo, '%')) " +
            "AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> buscarPorNomeOuProcessoWithEnderecos(
            @Param("nome") String nome,
            @Param("processo") String processo);

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (he.ativo = true OR he IS NULL) " +
            "ORDER BY c.situacao, c.nome")
    List<Custodiado> findAllIncludingArchivedWithEnderecos();

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.comarca = :comarca AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByComarcaWithEnderecos(@Param("comarca") String comarca);

    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.vara = :vara AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByVaraWithEnderecos(@Param("vara") String vara);

    // Queries sem JOIN FETCH - usadas quando não é necessário carregar endereços
    // Mais performáticas para operações que não precisam dos relacionamentos

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProcesso(@Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo " +
            "AND LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByProcessoAndNomeContainingIgnoreCase(
            @Param("processo") String processo,
            @Param("nome") String nome);

    @Query("SELECT c FROM Custodiado c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    @Query("SELECT c FROM Custodiado c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByRgAndSituacaoAtivo(@Param("rg") String rg);

    @Query("SELECT c FROM Custodiado c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    List<Custodiado> findByStatus(@Param("status") StatusComparecimento status);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = :data AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimento(@Param("data") LocalDate data);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento < :data AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoBefore(@Param("data") LocalDate data);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim " +
            "AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoBetween(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("SELECT c FROM Custodiado c WHERE " +
            "(LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "OR c.processo LIKE CONCAT('%', :processo, '%')) " +
            "AND c.situacao = 'ATIVO'")
    List<Custodiado> buscarPorNomeOuProcesso(
            @Param("nome") String nome,
            @Param("processo") String processo);

    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    long countByStatus(@Param("status") StatusComparecimento status);

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.nome")
    List<Custodiado> findAllByProcessoIncludingArchived(@Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo " +
            "AND c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAllByProcesso(@Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = CURRENT_DATE " +
            "AND c.situacao = 'ATIVO'")
    List<Custodiado> findComparecimentosHoje();

    @Query("SELECT c FROM Custodiado c WHERE " +
            "(c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) " +
            "AND c.situacao = 'ATIVO'")
    List<Custodiado> findInadimplentes();

    @Query("SELECT c.processo, COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO' " +
            "GROUP BY c.processo HAVING COUNT(c) > 1")
    List<Object[]> findProcessosComMultiplosCustodiados();

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' AND c.id NOT IN " +
            "(SELECT DISTINCT h.custodiado.id FROM HistoricoComparecimento h)")
    List<Custodiado> findCustodiadosSemHistorico();

    @Query("SELECT c FROM Custodiado c WHERE c.comarca = :comarca AND c.situacao = 'ATIVO'")
    List<Custodiado> findByComarca(@Param("comarca") String comarca);

    @Query("SELECT c FROM Custodiado c WHERE c.vara = :vara AND c.situacao = 'ATIVO'")
    List<Custodiado> findByVara(@Param("vara") String vara);

    @Query("SELECT c FROM Custodiado c ORDER BY c.situacao, c.nome")
    List<Custodiado> findAllIncludingArchived();

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAllActive();

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ARQUIVADO' ORDER BY c.nome")
    List<Custodiado> findAllArchived();

    List<Custodiado> findBySituacao(SituacaoCustodiado situacao);

    long countBySituacao(SituacaoCustodiado situacao);

    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    boolean existsByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    boolean existsByRgAndSituacaoAtivo(@Param("rg") String rg);

    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.cpf = :cpf " +
            "AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByCpfAndSituacaoAtivoAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.rg = :rg " +
            "AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByRgAndSituacaoAtivoAndIdNot(@Param("rg") String rg, @Param("id") Long id);

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ARQUIVADO' " +
            "AND c.dataComparecimentoInicial IS NOT NULL")
    List<Custodiado> findReactivatable();

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = :situacao AND c.status = :status")
    List<Custodiado> findBySituacaoAndStatus(
            @Param("situacao") SituacaoCustodiado situacao,
            @Param("status") StatusComparecimento status);

    @Query("SELECT COUNT(c) FROM Custodiado c")
    long countAllIncludingArchived();

    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO'")
    long countActive();

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo " +
            "ORDER BY c.situacao, c.nome")
    List<Custodiado> findByProcessoIncludingArchived(@Param("processo") String processo);

    @Override
    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO'")
    long count();

    @Override
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAll();
}