package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HistoricoComparecimentoRepository extends JpaRepository<HistoricoComparecimento, Long> {

    /**
     * Busca histórico de comparecimentos de uma pessoa ordenado por data (mais recente primeiro)
     */
    List<HistoricoComparecimento> findByPessoaOrderByDataComparecimentoDesc(Pessoa pessoa);

    /**
     * Busca histórico de comparecimentos por ID da pessoa
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByPessoaIdOrderByDataComparecimentoDesc(@Param("pessoaId") Long pessoaId);

    /**
     * Busca comparecimentos de uma data específica
     */
    List<HistoricoComparecimento> findByDataComparecimento(LocalDate data);

    /**
     * Busca comparecimentos por tipo de validação
     */
    List<HistoricoComparecimento> findByTipoValidacao(TipoValidacao tipo);

    /**
     * Busca comparecimentos por período
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.dataComparecimento BETWEEN :inicio AND :fim ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByDataComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Conta total de comparecimentos de uma pessoa
     */
    long countByPessoa(Pessoa pessoa);

    /**
     * Busca comparecimentos de uma pessoa por ID e data específica
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId AND h.dataComparecimento = :data")
    List<HistoricoComparecimento> findByPessoaIdAndDataComparecimento(@Param("pessoaId") Long pessoaId, @Param("data") LocalDate data);

    // === NOVAS CONSULTAS PARA MUDANÇA DE ENDEREÇO ===

    /**
     * Busca comparecimentos com mudança de endereço
     */
    List<HistoricoComparecimento> findByMudancaEnderecoTrue();

    /**
     * Busca comparecimentos com mudança de endereço de uma pessoa específica
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId AND h.mudancaEndereco = true ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByPessoaIdAndMudancaEnderecoTrue(@Param("pessoaId") Long pessoaId);

    /**
     * Busca comparecimentos sem mudança de endereço
     */
    List<HistoricoComparecimento> findByMudancaEnderecoFalse();

    /**
     * Conta comparecimentos com mudança de endereço por pessoa
     */
    @Query("SELECT COUNT(h) FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId AND h.mudancaEndereco = true")
    long countMudancasEnderecoPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca último comparecimento de uma pessoa
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId ORDER BY h.dataComparecimento DESC, h.horaComparecimento DESC LIMIT 1")
    HistoricoComparecimento findUltimoComparecimentoPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca primeiro comparecimento (cadastro inicial) de uma pessoa
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId AND h.tipoValidacao = 'CADASTRO_INICIAL'")
    HistoricoComparecimento findCadastroInicialPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca comparecimentos regulares de uma pessoa (excluindo cadastro inicial)
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId AND h.tipoValidacao != 'CADASTRO_INICIAL' ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findComparecimentosRegularesPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca comparecimentos atrasados (posteriores ao prazo esperado)
     */
    @Query("SELECT h FROM HistoricoComparecimento h JOIN h.pessoa p " +
            "WHERE h.dataComparecimento > p.proximoComparecimento " +
            "AND h.tipoValidacao != 'CADASTRO_INICIAL' " +
            "ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findComparecimentosAtrasados();

    /**
     * Busca comparecimentos por validador
     */
    List<HistoricoComparecimento> findByValidadoPorContainingIgnoreCaseOrderByDataComparecimentoDesc(String validador);

    /**
     * Busca comparecimentos por tipo e período
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.tipoValidacao = :tipo " +
            "AND h.dataComparecimento BETWEEN :inicio AND :fim " +
            "ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByTipoValidacaoAndPeriodo(@Param("tipo") TipoValidacao tipo,
                                                                @Param("inicio") LocalDate inicio,
                                                                @Param("fim") LocalDate fim);

    /**
     * Busca estatísticas de comparecimentos por tipo
     */
    @Query("SELECT h.tipoValidacao, COUNT(h) FROM HistoricoComparecimento h " +
            "WHERE h.dataComparecimento BETWEEN :inicio AND :fim " +
            "GROUP BY h.tipoValidacao")
    List<Object[]> findEstatisticasPorTipo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Busca estatísticas de mudanças de endereço por período
     */
    @Query("SELECT DATE(h.dataComparecimento), COUNT(h) FROM HistoricoComparecimento h " +
            "WHERE h.mudancaEndereco = true " +
            "AND h.dataComparecimento BETWEEN :inicio AND :fim " +
            "GROUP BY DATE(h.dataComparecimento) " +
            "ORDER BY DATE(h.dataComparecimento)")
    List<Object[]> findEstatisticasMudancasEnderecoPorPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Busca pessoas com mais mudanças de endereço
     */
    @Query("SELECT h.pessoa, COUNT(h) as totalMudancas FROM HistoricoComparecimento h " +
            "WHERE h.mudancaEndereco = true " +
            "GROUP BY h.pessoa " +
            "ORDER BY totalMudancas DESC")
    List<Object[]> findPessoasComMaisMudancasEndereco();

    /**
     * Verifica se pessoa já tem cadastro inicial
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId AND h.tipoValidacao = 'CADASTRO_INICIAL'")
    boolean existsCadastroInicialPorPessoa(@Param("pessoaId") Long pessoaId);

    /**
     * Busca comparecimentos com observações
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.observacoes IS NOT NULL AND LENGTH(TRIM(h.observacoes)) > 0 ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findComparecimentosComObservacoes();

    /**
     * Busca comparecimentos com anexos
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.anexos IS NOT NULL AND LENGTH(TRIM(h.anexos)) > 0 ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findComparecimentosComAnexos();

    /**
     * Média de dias entre comparecimentos de uma pessoa
     */
    @Query("SELECT AVG(FUNCTION('DATE_PART', 'day', h2.dataComparecimento - h1.dataComparecimento)) " +
            "FROM HistoricoComparecimento h1, HistoricoComparecimento h2 " +
            "WHERE h1.pessoa.id = :pessoaId AND h2.pessoa.id = :pessoaId " +
            "AND h1.tipoValidacao != 'CADASTRO_INICIAL' AND h2.tipoValidacao != 'CADASTRO_INICIAL' " +
            "AND h2.dataComparecimento > h1.dataComparecimento")
    Double findMediaDiasEntreComparecimentos(@Param("pessoaId") Long pessoaId);
}