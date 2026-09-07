package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dados processuais (status, próximo comparecimento, número) vivem em PROCESSOS;
 * as consultas derivam de lá via EXISTS. A listagem paginada usa SQL nativa no service.
 */
@Repository
public interface PessoaMonitoradaRepository extends JpaRepository<PessoaMonitorada, Long> {

    String TEM_PROCESSO_ATRASADO =
            "EXISTS (SELECT 1 FROM Processo p WHERE p.custodiado = c " +
            "AND p.situacaoProcesso = 'ATIVO' AND p.proximoComparecimento < CURRENT_DATE)";

    Optional<PessoaMonitorada> findByPublicId(UUID publicId);

    boolean existsByPublicId(UUID publicId);

    /** Ficha: processos junto para os campos derivados funcionarem fora da sessão. */
    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos WHERE c.publicId = :publicId")
    Optional<PessoaMonitorada> findByPublicIdComProcessos(@Param("publicId") UUID publicId);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos WHERE c.id = :id")
    Optional<PessoaMonitorada> findByIdComProcessos(@Param("id") Long id);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<PessoaMonitorada> findAllActive();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos ORDER BY c.situacao, c.nome")
    List<PessoaMonitorada> findAllIncludingArchivedWithEnderecos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<PessoaMonitorada> findAllWithEnderecosAtivos();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ARQUIVADO' ORDER BY c.nome")
    List<PessoaMonitorada> findAllArchived();

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ARQUIVADO' ORDER BY c.nome")
    List<PessoaMonitorada> findReactivatable();

    List<PessoaMonitorada> findBySituacao(SituacaoPessoaMonitorada situacao);

    long countBySituacao(SituacaoPessoaMonitorada situacao);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' AND " + TEM_PROCESSO_ATRASADO)
    List<PessoaMonitorada> findInadimplentesWithEnderecos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' AND NOT " + TEM_PROCESSO_ATRASADO)
    List<PessoaMonitorada> findEmConformidadeAtivos();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' AND EXISTS (SELECT 1 FROM Processo p WHERE p.custodiado = c " +
            "AND p.situacaoProcesso = 'ATIVO' AND p.proximoComparecimento = CURRENT_DATE)")
    List<PessoaMonitorada> findComparecimentosHoje();

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' AND (LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "OR EXISTS (SELECT 1 FROM Processo p WHERE p.custodiado = c " +
            "AND p.numeroProcesso LIKE CONCAT('%', :processo, '%')))")
    List<PessoaMonitorada> buscarPorNomeOuProcessoWithEnderecos(@Param("nome") String nome,
                                                                @Param("processo") String processo);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE c.situacao = 'ATIVO' AND EXISTS (SELECT 1 FROM Processo p WHERE p.custodiado = c " +
            "AND p.numeroProcesso = :processo)")
    List<PessoaMonitorada> findByProcesso(@Param("processo") String processo);

    @Query("SELECT DISTINCT c FROM PessoaMonitorada c LEFT JOIN FETCH c.processos " +
            "WHERE EXISTS (SELECT 1 FROM Processo p WHERE p.custodiado = c " +
            "AND p.numeroProcesso = :processo) ORDER BY c.situacao, c.nome")
    List<PessoaMonitorada> findByProcessoIncludingArchived(@Param("processo") String processo);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    Optional<PessoaMonitorada> findByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    Optional<PessoaMonitorada> findByCpfAndSituacao(String cpf, SituacaoPessoaMonitorada situacao);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO'")
    boolean existsByCpfAndSituacaoAtivo(@Param("cpf") String cpf);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.rg = :rg AND c.situacao = 'ATIVO'")
    boolean existsByRgAndSituacaoAtivo(@Param("rg") String rg);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.cpf = :cpf AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByCpfAndSituacaoAtivoAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    @Query("SELECT COUNT(c) > 0 FROM PessoaMonitorada c WHERE c.rg = :rg AND c.situacao = 'ATIVO' AND c.id != :id")
    boolean existsByRgAndSituacaoAtivoAndIdNot(@Param("rg") String rg, @Param("id") Long id);

    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' AND c.id NOT IN " +
            "(SELECT DISTINCT h.custodiado.id FROM HistoricoComparecimento h)")
    List<PessoaMonitorada> findPessoasMonitoradasSemHistorico();

    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO'")
    long countActive();

    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' AND " + TEM_PROCESSO_ATRASADO)
    long countInadimplentesAtivos();

    @Override
    @Query("SELECT COUNT(c) FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO'")
    long count();

    @Override
    @Query("SELECT c FROM PessoaMonitorada c WHERE c.situacao = 'ATIVO' ORDER BY c.nome")
    List<PessoaMonitorada> findAll();
}
