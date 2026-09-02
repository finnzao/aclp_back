package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PessoaMonitoradaRepository
        extends JpaRepository<PessoaMonitorada, Long>,
                JpaSpecificationExecutor<PessoaMonitorada> {

    Optional<PessoaMonitorada> findByPublicId(UUID publicId);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.publicId = :publicId AND (he.ativo = true OR he IS NULL)")
    Optional<PessoaMonitorada> findByPublicIdWithEnderecoAtivo(@Param("publicId") UUID publicId);

    boolean existsByPublicId(UUID publicId);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.id = :id AND (he.ativo = true OR he IS NULL)")
    Optional<PessoaMonitorada> findByIdWithEnderecoAtivo(@Param("id") Long id);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL) ORDER BY c.nome")
    List<PessoaMonitorada> findAllActiveWithEnderecos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL) ORDER BY c.nome")
    List<PessoaMonitorada> findAllWithEnderecosAtivos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.processo = :processo AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> findByProcessoWithEnderecos(@Param("processo") String processo);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.status = :status AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> findByStatusWithEnderecos(@Param("status") StatusComparecimento status);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.proximoComparecimento = CURRENT_DATE AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> findComparecimentosHojeWithEnderecos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) " +
            "AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> findInadimplentesWithEnderecos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "OR c.processo LIKE CONCAT('%', :processo, '%')) " +
            "AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> buscarPorNomeOuProcessoWithEnderecos(@Param("nome") String nome, @Param("processo") String processo);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE (he.ativo = true OR he IS NULL) ORDER BY c.situacao, c.nome")
    List<PessoaMonitorada> findAllIncludingArchivedWithEnderecos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.comarca = :comarca AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> findByComarcaWithEnderecos(@Param("comarca") String comarca);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c " +
            "LEFT JOIN FETCH c.historicoEnderecos he " +
            "WHERE c.vara = :vara AND c.situacao = 'ATIVO' AND (he.ativo = true OR he IS NULL)")
    List<PessoaMonitorada> findByVaraWithEnderecos(@Param("vara") String vara);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    Optional<PessoaMonitorada> findByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    Optional<PessoaMonitorada> findByCpfAndSituacao(String cpf, SituacaoPessoaMonitorada situacao);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    Optional<PessoaMonitorada> findByRgAndSituacaoAtivo(@Param("rg") String rg);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<PessoaMonitorada> findAllActive();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ARQUIVADO' ORDER BY c.nome")
    List<PessoaMonitorada> findAllArchived();

    List<PessoaMonitorada> findBySituacao(SituacaoPessoaMonitorada situacao);
    long countBySituacao(SituacaoPessoaMonitorada situacao);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    boolean existsByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    boolean existsByRgAndSituacaoAtivo(@Param("rg") String rg);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByCpfAndSituacaoAtivoAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.rg = :rg AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByRgAndSituacaoAtivoAndIdNot(@Param("rg") String rg, @Param("id") Long id);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' " +
            "AND (LOWER(c.nome) LIKE LOWER(CONCAT('%',:termo,'%')) OR c.cpf LIKE CONCAT('%',:termo,'%'))")
    List<PessoaMonitorada> buscarPorNomeOuCpf(@Param("termo") String termo);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.processo = :processo AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByProcesso(@Param("processo") String processo);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByStatus(@Param("status") StatusComparecimento status);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.proximoComparecimento = CURRENT_DATE AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findComparecimentosHoje();

    @Query("SELECT c FROM PessoaMonitorada c WHERE " +
            "(c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE) AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findInadimplentes();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.proximoComparecimento = :data AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByProximoComparecimento(@Param("data") LocalDate data);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.proximoComparecimento < :data AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByProximoComparecimentoBefore(@Param("data") LocalDate data);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT c FROM PessoaMonitorada c WHERE " +
            "(LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "OR c.processo LIKE CONCAT('%', :processo, '%')) AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> buscarPorNomeOuProcesso(@Param("nome") String nome, @Param("processo") String processo);

    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.status = :status AND c.situacao = 'ATIVO'")
    long countByStatus(@Param("status") StatusComparecimento status);

    /** Inadimplente pela DATA, não pelo campo persistido (que pode estar defasado). */
    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' " +
            "AND (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE)")
    long countInadimplentesAtivos();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.processo = :processo ORDER BY c.nome")
    List<PessoaMonitorada> findAllByProcessoIncludingArchived(@Param("processo") String processo);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.processo = :processo AND c.situacao = 'ATIVO' ORDER BY c.nome")
    List<PessoaMonitorada> findAllByProcesso(@Param("processo") String processo);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' AND c.id NOT IN " +
            "(SELECT DISTINCT h.custodiado.id FROM HistoricoComparecimento h)")
    List<PessoaMonitorada> findPessoasMonitoradasSemHistorico();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.comarca = :comarca AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByComarca(@Param("comarca") String comarca);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.vara = :vara AND c.situacao = 'ATIVO'")
    List<PessoaMonitorada> findByVara(@Param("vara") String vara);

    @Query("SELECT c FROM PessoaMonitorada c ORDER BY c.situacao, c.nome")
    List<PessoaMonitorada> findAllIncludingArchived();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ARQUIVADO' AND c.dataComparecimentoInicial IS NOT NULL")
    List<PessoaMonitorada> findReactivatable();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = :situacao AND c.status = :status")
    List<PessoaMonitorada> findBySituacaoAndStatus(@Param("situacao") SituacaoPessoaMonitorada situacao,
                                              @Param("status") StatusComparecimento status);

    @Query("SELECT COUNT(c) FROM PessoaMonitorada c")
    long countAllIncludingArchived();

    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO'")
    long countActive();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.processo = :processo ORDER BY c.situacao, c.nome")
    List<PessoaMonitorada> findByProcessoIncludingArchived(@Param("processo") String processo);

    @Override
    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO'")
    long count();

    @Override
    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<PessoaMonitorada> findAll();
}
