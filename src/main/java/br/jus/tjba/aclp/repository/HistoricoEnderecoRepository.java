package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricoEnderecoRepository extends JpaRepository<HistoricoEndereco, Long> {

    /**
     * Busca histórico de endereços de uma pessoa ordenado por data de início (mais recente primeiro)
     */
    List<HistoricoEndereco> findByPessoaOrderByDataInicioDesc(Pessoa pessoa);

    /**
     * Busca histórico de endereços de uma pessoa por ID ordenado por data de início
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.pessoa.id = :pessoaId ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findByPessoaIdOrderByDataInicioDesc(@Param("pessoaId") Long pessoaId);

    /**
     * Busca o endereço ativo atual de uma pessoa (sem data de fim)
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.pessoa.id = :pessoaId AND h.dataFim IS NULL")
    Optional<HistoricoEndereco> findEnderecoAtivoPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca endereços históricos de uma pessoa (com data de fim)
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.pessoa.id = :pessoaId AND h.dataFim IS NOT NULL ORDER BY h.dataFim DESC")
    List<HistoricoEndereco> findEnderecosHistoricosPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca endereços por período específico
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.pessoa.id = :pessoaId " +
            "AND h.dataInicio <= :dataFim " +
            "AND (h.dataFim IS NULL OR h.dataFim >= :dataInicio) " +
            "ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findEnderecosPorPeriodo(@Param("pessoaId") Long pessoaId,
                                                    @Param("dataInicio") LocalDate dataInicio,
                                                    @Param("dataFim") LocalDate dataFim);

    /**
     * Busca endereços por CEP
     */
    List<HistoricoEndereco> findByCep(String cep);

    /**
     * Busca endereços por cidade
     */
    List<HistoricoEndereco> findByCidade(String cidade);

    /**
     * Busca endereços por estado
     */
    List<HistoricoEndereco> findByEstado(String estado);

    /**
     * Conta quantos endereços uma pessoa já teve
     */
    long countByPessoa(Pessoa pessoa);

    /**
     * Busca endereços que estavam ativos em uma data específica
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.dataInicio <= :data " +
            "AND (h.dataFim IS NULL OR h.dataFim > :data)")
    List<HistoricoEndereco> findEnderecosAtivosPorData(@Param("data") LocalDate data);

    /**
     * Busca mudanças de endereço em um período
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.dataInicio BETWEEN :inicio AND :fim " +
            "ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findMudancasEnderecoPorPeriodo(@Param("inicio") LocalDate inicio,
                                                           @Param("fim") LocalDate fim);

    /**
     * Busca endereços por comparecimento específico
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.historicoComparecimento.id = :comparecimentoId")
    List<HistoricoEndereco> findByHistoricoComparecimentoId(@Param("comparecimentoId") Long comparecimentoId);

    /**
     * Busca pessoas que moram/moraram em uma cidade específica
     */
    @Query("SELECT DISTINCT h.pessoa FROM HistoricoEndereco h WHERE h.cidade = :cidade")
    List<Pessoa> findPessoasPorCidade(@Param("cidade") String cidade);

    /**
     * Busca pessoas que moram/moraram em um estado específico
     */
    @Query("SELECT DISTINCT h.pessoa FROM HistoricoEndereco h WHERE h.estado = :estado")
    List<Pessoa> findPessoasPorEstado(@Param("estado") String estado);

    /**
     * Busca endereços com motivação específica
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.motivoAlteracao LIKE %:motivo% ORDER BY h.dataInicio DESC")
    List<HistoricoEndereco> findByMotivoAlteracaoContaining(@Param("motivo") String motivo);

    /**
     * Busca estatísticas de mudanças por pessoa
     */
    @Query("SELECT h.pessoa.id, COUNT(h) as totalMudancas FROM HistoricoEndereco h " +
            "WHERE h.dataFim IS NOT NULL GROUP BY h.pessoa.id ORDER BY totalMudancas DESC")
    List<Object[]> findEstatisticasMudancasPorPessoa();

    /**
     * Busca endereços que duraram menos que X dias
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.dataFim IS NOT NULL " +
            "AND FUNCTION('DATE_PART', 'day', h.dataFim - h.dataInicio) < :dias")
    List<HistoricoEndereco> findEnderecosComPoucasDuracao(@Param("dias") long dias);

    /**
     * Verifica se existe sobreposição de endereços para uma pessoa
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoEndereco h WHERE h.pessoa.id = :pessoaId " +
            "AND h.id != :enderecoId " +
            "AND h.dataInicio <= :dataFim " +
            "AND (h.dataFim IS NULL OR h.dataFim >= :dataInicio)")
    boolean existeSobreposicaoEndereco(@Param("pessoaId") Long pessoaId,
                                       @Param("enderecoId") Long enderecoId,
                                       @Param("dataInicio") LocalDate dataInicio,
                                       @Param("dataFim") LocalDate dataFim);

    /**
     * Busca o último endereço anterior a uma data específica
     */
    @Query("SELECT h FROM HistoricoEndereco h WHERE h.pessoa.id = :pessoaId " +
            "AND h.dataInicio <= :data ORDER BY h.dataInicio DESC LIMIT 1")
    Optional<HistoricoEndereco> findUltimoEnderecoAnteriorData(@Param("pessoaId") Long pessoaId,
                                                               @Param("data") LocalDate data);
}