package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
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
     * Busca histórico de comparecimentos de um custodiado ordenado por data (mais recente primeiro)
     */
    List<HistoricoComparecimento> findByCustodiadoOrderByDataComparecimentoDesc(Custodiado custodiado);

    /**
     * Busca histórico de comparecimentos por ID do custodiado
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByCustodiadoIdOrderByDataComparecimentoDesc(@Param("custodiadoId") Long custodiadoId);

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
     * Conta total de comparecimentos de um custodiado
     */
    long countByCustodiado(Custodiado custodiado);

    /**
     * Busca comparecimentos de um custodiado por ID e data específica
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId AND h.dataComparecimento = :data")
    List<HistoricoComparecimento> findByCustodiadoIdAndDataComparecimento(@Param("custodiadoId") Long custodiadoId, @Param("data") LocalDate data);

    /**
     * Busca comparecimentos com mudança de endereço
     */
    List<HistoricoComparecimento> findByMudancaEnderecoTrue();

    /**
     * Busca comparecimentos com mudança de endereço de um custodiado específico
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId AND h.mudancaEndereco = true ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByCustodiadoIdAndMudancaEnderecoTrue(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca comparecimentos sem mudança de endereço
     */
    List<HistoricoComparecimento> findByMudancaEnderecoFalse();

    /**
     * Conta comparecimentos com mudança de endereço por custodiado
     */
    @Query("SELECT COUNT(h) FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId AND h.mudancaEndereco = true")
    long countMudancasEnderecoPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca último comparecimento de um custodiado
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId ORDER BY h.dataComparecimento DESC, h.horaComparecimento DESC LIMIT 1")
    HistoricoComparecimento findUltimoComparecimentoPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca primeiro comparecimento (cadastro inicial) de um custodiado
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId AND h.tipoValidacao = 'CADASTRO_INICIAL'")
    HistoricoComparecimento findCadastroInicialPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca comparecimentos regulares de um custodiado (excluindo cadastro inicial)
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId AND h.tipoValidacao != 'CADASTRO_INICIAL' ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findComparecimentosRegularesPorCustodiado(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca comparecimentos atrasados (posteriores ao prazo esperado)
     */
    @Query("SELECT h FROM HistoricoComparecimento h JOIN h.custodiado c " +
            "WHERE h.dataComparecimento > c.proximoComparecimento " +
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
     * Busca custodiados com mais mudanças de endereço
     */
    @Query("SELECT h.custodiado, COUNT(h) as totalMudancas FROM HistoricoComparecimento h " +
            "WHERE h.mudancaEndereco = true " +
            "GROUP BY h.custodiado " +
            "ORDER BY totalMudancas DESC")
    List<Object[]> findCustodiadosComMaisMudancasEndereco();

    /**
     * Verifica se custodiado já tem cadastro inicial
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId AND h.tipoValidacao = 'CADASTRO_INICIAL'")
    boolean existsCadastroInicialPorCustodiado(@Param("custodiadoId") Long custodiadoId);

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
     * Busca comparecimentos para cálculo de média
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.custodiado.id = :custodiadoId " +
            "AND h.tipoValidacao != 'CADASTRO_INICIAL' " +
            "ORDER BY h.dataComparecimento ASC")
    List<HistoricoComparecimento> findComparecimentosParaCalculoMedia(@Param("custodiadoId") Long custodiadoId);

    /**
     * Busca todos os primeiros comparecimentos (cadastros iniciais)
     */
    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.tipoValidacao = 'CADASTRO_INICIAL' ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findAllCadastrosIniciais();

    /**
     * Conta custodiados sem cadastro inicial
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Custodiado c WHERE c.id NOT IN " +
            "(SELECT h.custodiado.id FROM HistoricoComparecimento h WHERE h.tipoValidacao = 'CADASTRO_INICIAL')")
    long countCustodiadosSemCadastroInicial();
}