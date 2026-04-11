package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessoRepository extends JpaRepository<Processo, Long> {

    // Processos ativos de um custodiado
    @Query("SELECT p FROM Processo p WHERE p.custodiado.id = :custodiadoId AND p.situacaoProcesso = 'ATIVO' ORDER BY p.proximoComparecimento")
    List<Processo> findProcessosAtivosByCustodiado(@Param("custodiadoId") Long custodiadoId);

    Optional<Processo> findByNumeroProcesso(String numeroProcesso);

    // Inadimplentes com JOIN FETCH (evita N+1)
    @Query("SELECT p FROM Processo p JOIN FETCH p.custodiado c WHERE p.status = 'INADIMPLENTE' AND p.situacaoProcesso = 'ATIVO'")
    List<Processo> findInadimplentesComCustodiado();

    // Comparecimentos para hoje com JOIN FETCH
    @Query("SELECT p FROM Processo p JOIN FETCH p.custodiado c WHERE p.proximoComparecimento = CURRENT_DATE AND p.situacaoProcesso = 'ATIVO'")
    List<Processo> findComparecimentosHojeComCustodiado();

    // Listagem paginada com filtros — query principal do sistema
    @Query(value = "SELECT p FROM Processo p JOIN FETCH p.custodiado c " +
            "WHERE p.situacaoProcesso = 'ATIVO' " +
            "AND (:termo IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%',:termo,'%')) " +
            "     OR c.cpf LIKE CONCAT('%',:termo,'%') " +
            "     OR p.numeroProcesso LIKE CONCAT('%',:termo,'%')) " +
            "AND (:status IS NULL OR p.status = :status)",
            countQuery = "SELECT COUNT(p) FROM Processo p JOIN p.custodiado c " +
                    "WHERE p.situacaoProcesso = 'ATIVO' " +
                    "AND (:termo IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%',:termo,'%')) " +
                    "     OR c.cpf LIKE CONCAT('%',:termo,'%') " +
                    "     OR p.numeroProcesso LIKE CONCAT('%',:termo,'%')) " +
                    "AND (:status IS NULL OR p.status = :status)")
    Page<Processo> findComFiltros(@Param("termo") String termo, @Param("status") StatusComparecimento status, Pageable pageable);

    // Contadores para dashboard
    long countBySituacaoProcesso(SituacaoProcesso situacao);

    @Query("SELECT COUNT(p) FROM Processo p WHERE p.status = :status AND p.situacaoProcesso = 'ATIVO'")
    long countByStatusAtivo(@Param("status") StatusComparecimento status);

    // Processos com comparecimento entre datas
    @Query("SELECT p FROM Processo p WHERE p.proximoComparecimento BETWEEN :inicio AND :fim AND p.situacaoProcesso = 'ATIVO'")
    List<Processo> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // Para job de verificação de atrasados
    @Query("SELECT p FROM Processo p WHERE p.proximoComparecimento < CURRENT_DATE AND p.status = 'EM_CONFORMIDADE' AND p.situacaoProcesso = 'ATIVO'")
    List<Processo> findAtrasadosParaVerificacao();

    List<Processo> findByCustodiado_IdOrderByCriadoEmDesc(Long custodiadoId);

    // Buscar por ID com custodiado carregado
    @Query("SELECT p FROM Processo p JOIN FETCH p.custodiado c WHERE p.id = :id")
    Optional<Processo> findByIdComCustodiado(@Param("id") Long id);

    // Todos processos ativos
    @Query("SELECT p FROM Processo p JOIN FETCH p.custodiado c WHERE p.situacaoProcesso = 'ATIVO' ORDER BY p.proximoComparecimento")
    List<Processo> findAllAtivosComCustodiado();

    // Verificar se custodiado tem processos ativos não encerrados
    @Query("SELECT COUNT(p) > 0 FROM Processo p WHERE p.custodiado.id = :custodiadoId AND p.situacaoProcesso NOT IN ('ENCERRADO', 'SUSPENSO')")
    boolean existsProcessosAtivos(@Param("custodiadoId") Long custodiadoId);

    /**
     * CORREÇÃO DE PERFORMANCE: Busca processos de múltiplos custodiados em uma única query.
     * Substitui o padrão N+1 de chamar findByCustodiadoId() em loop.
     *
     * Usado pelo endpoint POST /processos/batch e internamente para evitar
     * que o frontend precise fazer dezenas de requisições individuais.
     */
    @Query("SELECT p FROM Processo p JOIN FETCH p.custodiado c " +
           "WHERE p.custodiado.id IN :custodiadoIds " +
           "ORDER BY p.custodiado.id, p.criadoEm DESC")
    List<Processo> findByCustodiadoIdIn(@Param("custodiadoIds") List<Long> custodiadoIds);

    /**
     * CORREÇÃO DE PERFORMANCE: Busca apenas processos ativos de múltiplos custodiados.
     * Versão filtrada da busca em lote.
     */
    @Query("SELECT p FROM Processo p JOIN FETCH p.custodiado c " +
           "WHERE p.custodiado.id IN :custodiadoIds " +
           "AND p.situacaoProcesso = :situacao " +
           "ORDER BY p.custodiado.id, p.criadoEm DESC")
    List<Processo> findByCustodiadoIdInAndSituacaoProcesso(
        @Param("custodiadoIds") List<Long> custodiadoIds,
        @Param("situacao") SituacaoProcesso situacao
    );

    /**
     * CORREÇÃO DE PERFORMANCE: Busca processos de um custodiado por situação.
     * Usado como fallback na conversão de comparecimentos para DTO.
     */
    @Query("SELECT p FROM Processo p WHERE p.custodiado.id = :custodiadoId " +
           "AND p.situacaoProcesso = :situacao ORDER BY p.criadoEm DESC")
    List<Processo> findByCustodiadoIdAndSituacaoProcesso(
        @Param("custodiadoId") Long custodiadoId,
        @Param("situacao") SituacaoProcesso situacao
    );
}
