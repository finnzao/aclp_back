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

    // Busca por processo - pode retornar múltiplos custodiados ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProcesso(@Param("processo") String processo);

    // Busca por processo e nome para identificar custodiado específico ATIVO
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.nome LIKE %:nome% AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByProcessoAndNomeContainingIgnoreCase(@Param("processo") String processo, @Param("nome") String nome);

    //  Busca por CPF considerando apenas ATIVOS para validar duplicidade
    @Query("SELECT c FROM Custodiado c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    //  Busca por RG considerando apenas ATIVOS para validar duplicidade
    @Query("SELECT c FROM Custodiado c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    Optional<Custodiado> findByRgAndSituacaoAtivo(@Param("rg") String rg);

    // Busca por status considerando apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    List<Custodiado> findByStatus(@Param("status") StatusComparecimento status);

    // Busca por próximo comparecimento considerando apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = :data AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimento(@Param("data") LocalDate data);

    // Busca inadimplentes (próximo comparecimento anterior a hoje) - apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento < :data AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoBefore(@Param("data") LocalDate data);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim AND c.situacao = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT c FROM Custodiado c WHERE (c.nome LIKE %:nome% OR c.processo LIKE %:processo%) AND c.situacao = 'ATIVO'")
    List<Custodiado> buscarPorNomeOuProcesso(@Param("nome") String nome, @Param("processo") String processo);

    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    long countByStatus(@Param("status") StatusComparecimento status);

    // Busca todos os custodiados de um mesmo processo (incluindo ARQUIVADOS)
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.nome")
    List<Custodiado> findAllByProcessoIncludingArchived(@Param("processo") String processo);

    // Busca todos os custodiados ATIVOS de um mesmo processo
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo AND c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAllByProcesso(@Param("processo") String processo);

    // Busca custodiados com comparecimento hoje - apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = CURRENT_DATE AND c.situacao = 'ATIVO'")
    List<Custodiado> findComparecimentosHoje();

    // Busca custodiados inadimplentes - apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) AND c.situacao = 'ATIVO'")
    List<Custodiado> findInadimplentes();

    // Conta custodiados por processo
    @Query("SELECT c.processo, COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO' GROUP BY c.processo HAVING COUNT(c) > 1")
    List<Object[]> findProcessosComMultiplosCustodiados();

    // Busca custodiados sem histórico de comparecimento - apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' AND c.id NOT IN " +
            "(SELECT DISTINCT h.custodiado.id FROM HistoricoComparecimento h)")
    List<Custodiado> findCustodiadosSemHistorico();

    // Busca custodiados por comarca - apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.comarca = :comarca AND c.situacao = 'ATIVO'")
    List<Custodiado> findByComarca(@Param("comarca") String comarca);

    // Busca custodiados por vara - apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.vara = :vara AND c.situacao = 'ATIVO'")
    List<Custodiado> findByVara(@Param("vara") String vara);

    /**
     * Busca TODOS os custodiados (ATIVOS + ARQUIVADOS)
     */
    @Query("SELECT c FROM Custodiado c ORDER BY c.situacao, c.nome")
    List<Custodiado> findAllIncludingArchived();

    /**
     * Busca apenas custodiados ATIVOS (método padrão)
     */
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAllActive();

    /**
     * Busca apenas custodiados ARQUIVADOS
     */
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ARQUIVADO' ORDER BY c.nome")
    List<Custodiado> findAllArchived();

    /**
     * Busca por situação específica
     */
    List<Custodiado> findBySituacao(SituacaoCustodiado situacao);

    /**
     * Conta custodiados por situação
     */
    long countBySituacao(SituacaoCustodiado situacao);

    /**
     *  Verifica duplicidade de CPF considerando apenas ATIVOS
     */
    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    boolean existsByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    /**
     *  Verifica duplicidade de RG considerando apenas ATIVOS
     */
    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    boolean existsByRgAndSituacaoAtivo(@Param("rg") String rg);

    /**
     *  Verifica duplicidade de CPF excluindo um ID específico (para updates)
     */
    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByCpfAndSituacaoAtivoAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    /**
     *  Verifica duplicidade de RG excluindo um ID específico (para updates)
     */
    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.rg = :rg AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByRgAndSituacaoAtivoAndIdNot(@Param("rg") String rg, @Param("id") Long id);

    /**
     * Busca custodiados que podem ser reativados (ARQUIVADOS com dados válidos)
     */
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ARQUIVADO' AND c.dataComparecimentoInicial IS NOT NULL")
    List<Custodiado> findReactivatable();

    /**
     * Busca custodiados por situação e status
     */
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = :situacao AND c.status = :status")
    List<Custodiado> findBySituacaoAndStatus(@Param("situacao") SituacaoCustodiado situacao,
                                             @Param("status") StatusComparecimento status);

    /**
     * Conta total de custodiados incluindo arquivados
     */
    @Query("SELECT COUNT(c) FROM Custodiado c")
    long countAllIncludingArchived();

    /**
     * Conta apenas custodiados ativos
     */
    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO'")
    long countActive();

    /**
     * Busca por processo incluindo arquivados (para relatórios completos)
     */
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.situacao, c.nome")
    List<Custodiado> findByProcessoIncludingArchived(@Param("processo") String processo);

    /**
     * Override do método count() padrão para retornar apenas ATIVOS
     */
    @Override
    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.situacao = 'ATIVO'")
    long count();

    /**
     * Override do método findAll() padrão para retornar apenas ATIVOS
     */
    @Override
    @Query("SELECT c FROM Custodiado c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<Custodiado> findAll();
}