package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustodiadoRepository extends JpaRepository<Custodiado, Long> {

    // ========== QUERIES OTIMIZADAS COM JOIN FETCH ==========

    /**
     * ✅ OTIMIZADO: Busca custodiado por ID com endereço em uma única query
     * Evita N+1 ao carregar o endereço ativo junto com o custodiado
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.id = :id AND (he.ativo = true OR he IS NULL)")
    Optional<Custodiado> findByIdWithEnderecoAtivo(@Param("id") Long id);

    /**
     * ✅ OTIMIZADO: Busca todos os custodiados ATIVOS com endereços em uma única query
     * Resolve o problema N+1 queries ao buscar todos os endereços de uma vez
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL) " +
            "ORDER BY c.nome")
    List<Custodiado> findAllActiveWithEnderecos();

    /**
     * ✅ OTIMIZADO: Busca por processo com endereços (múltiplos custodiados)
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.processo = :processo AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByProcessoWithEnderecos(@Param("processo") String processo);

    /**
     * ✅ OTIMIZADO: Busca por status com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.status = :status AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByStatusWithEnderecos(@Param("status") StatusComparecimento status);

    /**
     * ✅ OTIMIZADO: Busca comparecimentos de hoje com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.proximoComparecimento = CURRENT_DATE AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findComparecimentosHojeWithEnderecos();

    /**
     * ✅ OTIMIZADO: Busca inadimplentes com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) " +
            "AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findInadimplentesWithEnderecos();

    /**
     * ✅ OTIMIZADO: Busca por nome ou processo com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (c.nome LIKE %:nome% OR c.processo LIKE %:processo%) " +
            "AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> buscarPorNomeOuProcessoWithEnderecos(@Param("nome") String nome,
                                                          @Param("processo") String processo);

    /**
     * ✅ OTIMIZADO: Busca todos incluindo arquivados com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE he.ativo = true OR he IS NULL " +
            "ORDER BY c.situacao, c.nome")
    List<Custodiado> findAllIncludingArchivedWithEnderecos();

    /**
     * ✅ OTIMIZADO: Busca por comarca com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.comarca = :comarca AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByComarcaWithEnderecos(@Param("comarca") String comarca);

    /**
     * ✅ OTIMIZADO: Busca por vara com endereços
     */
    @Query("SELECT DISTINCT c FROM Custodiado c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.vara = :vara AND c.situacao = 'ATIVO' " +
            "AND (he.ativo = true OR he IS NULL)")
    List<Custodiado> findByVaraWithEnderecos(@Param("vara") String vara);

    // ========== QUERIES ORIGINAIS (SEM ENDEREÇOS) ==========
    // Mantidas para casos onde não é necessário carregar endereços

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProcesso(@Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.nome LIKE %:nome% AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByProcessoAndNomeContainingIgnoreCase(@Param("processo") String processo, @Param("nome") String nome);

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

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT c FROM Custodiado c WHERE (c.nome LIKE %:nome% OR c.processo LIKE %:processo%) AND c.situacao = 'ATIVO'")
    List<Custodiado> buscarPorNomeOuProcesso(@Param("nome") String nome, @Param("processo") String processo);

    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    long countByStatus(@Param("status") StatusComparecimento status);

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.nome")
    List<Custodiado> findAllByProcessoIncludingArchived(@Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAllByProcesso(@Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = CURRENT_DATE AND c.situacao = 'ATIVO'")
    List<Custodiado> findComparecimentosHoje();

    @Query("SELECT c FROM Custodiado c WHERE (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) AND c.situacao = 'ATIVO'")
    List<Custodiado> findInadimplentes();

    @Query("SELECT c.processo, COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO' GROUP BY c.processo HAVING COUNT(c) > 1")
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

    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByCpfAndSituacaoAtivoAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.rg = :rg AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByRgAndSituacaoAtivoAndIdNot(@Param("rg") String rg, @Param("id") Long id);

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ARQUIVADO' AND c.dataComparecimentoInicial IS NOT NULL")
    List<Custodiado> findReactivatable();

    @Query("SELECT c FROM Custodiado c WHERE c.situacao = :situacao AND c.status = :status")
    List<Custodiado> findBySituacaoAndStatus(@Param("situacao") SituacaoCustodiado situacao,
                                             @Param("status") StatusComparecimento status);

    @Query("SELECT COUNT(c) FROM Custodiado c")
    long countAllIncludingArchived();

    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO'")
    long countActive();

    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.situacao, c.nome")
    List<Custodiado> findByProcessoIncludingArchived(@Param("processo") String processo);

    @Override
    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO'")
    long count();

    @Override
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAll();
}