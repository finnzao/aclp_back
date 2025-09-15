package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.StatusCustodiado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustodiadoRepository extends JpaRepository<Custodiado, Long> {

    // Busca por processo - pode retornar múltiplos custodiados
    List<Custodiado> findByProcesso(String processo);

    // Busca por processo e nome para identificar custodiado específico
    Optional<Custodiado> findByProcessoAndNomeContainingIgnoreCase(String processo, String nome);

    // Busca por CPF apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.cpf = :cpf AND c.statusCustodiado = 'ATIVO'")
    Optional<Custodiado> findByCpfAndAtivo(@Param("cpf") String cpf);

    // Busca por RG apenas ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.rg = :rg AND c.statusCustodiado = 'ATIVO'")
    Optional<Custodiado> findByRgAndAtivo(@Param("rg") String rg);

    // Busca por CPF (qualquer status) - mantido para compatibilidade
    Optional<Custodiado> findByCpf(String cpf);

    // Busca por RG (qualquer status) - mantido para compatibilidade
    Optional<Custodiado> findByRg(String rg);

    // Busca apenas custodiados ATIVOS
    @Query("SELECT c FROM Custodiado c WHERE c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findAllAtivos();

    // Busca todos (ATIVOS e ARQUIVADOS)
    @Query("SELECT c FROM Custodiado c ORDER BY c.statusCustodiado ASC, c.nome ASC")
    List<Custodiado> findAllCustodiados();

    // Busca por status do custodiado
    List<Custodiado> findByStatusCustodiado(StatusCustodiado statusCustodiado);

    // Busca por status de comparecimento (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.status = :status AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findByStatusAndAtivo(@Param("status") StatusComparecimento status);

    // Busca original por status (mantida para compatibilidade)
    List<Custodiado> findByStatus(StatusComparecimento status);

    // Busca por próximo comparecimento (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = :data AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoAndAtivo(@Param("data") LocalDate data);

    List<Custodiado> findByProximoComparecimento(LocalDate data);

    // Busca inadimplentes (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento < :data AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findInadimplentesAtivos(@Param("data") LocalDate data);

    List<Custodiado> findByProximoComparecimentoBefore(LocalDate data);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findByProximoComparecimentoBetweenAndAtivo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim")
    List<Custodiado> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // Busca por nome ou processo (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE (c.nome LIKE %:nome% OR c.processo LIKE %:processo%) AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> buscarPorNomeOuProcessoAtivos(@Param("nome") String nome, @Param("processo") String processo);

    @Query("SELECT c FROM Custodiado c WHERE c.nome LIKE %:nome% OR c.processo LIKE %:processo%")
    List<Custodiado> buscarPorNomeOuProcesso(@Param("nome") String nome, @Param("processo") String processo);

    // Contadores
    long countByStatus(StatusComparecimento status);

    long countByStatusCustodiado(StatusCustodiado statusCustodiado);

    @Query("SELECT COUNT(c) FROM Custodiado c WHERE c.status = :status AND c.statusCustodiado = 'ATIVO'")
    long countByStatusAndAtivo(@Param("status") StatusComparecimento status);

    // Busca todos os custodiados de um mesmo processo
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.nome")
    List<Custodiado> findAllByProcesso(@Param("processo") String processo);

    // Busca custodiados com comparecimento hoje (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = CURRENT_DATE AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findComparecimentosHoje();

    // Busca custodiados inadimplentes (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.statusCustodiado = 'ATIVO' AND (c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE)")
    List<Custodiado> findInadimplentes();

    // Conta custodiados por processo
    @Query("SELECT c.processo, COUNT(c) FROM Custodiado c WHERE c.statusCustodiado = 'ATIVO' GROUP BY c.processo HAVING COUNT(c) > 1")
    List<Object[]> findProcessosComMultiplosCustodiadosAtivos();

    // Busca custodiados sem histórico de comparecimento (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.statusCustodiado = 'ATIVO' AND c.id NOT IN " +
            "(SELECT DISTINCT h.custodiado.id FROM HistoricoComparecimento h)")
    List<Custodiado> findCustodiadosSemHistorico();

    // Busca custodiados por comarca (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.comarca = :comarca AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findByComarcaAndAtivo(@Param("comarca") String comarca);

    List<Custodiado> findByComarca(String comarca);

    // Busca custodiados por vara (apenas ATIVOS)
    @Query("SELECT c FROM Custodiado c WHERE c.vara = :vara AND c.statusCustodiado = 'ATIVO'")
    List<Custodiado> findByVaraAndAtivo(@Param("vara") String vara);

    List<Custodiado> findByVara(String vara);

    // Busca custodiados arquivados
    @Query("SELECT c FROM Custodiado c WHERE c.statusCustodiado = 'ARQUIVADO' ORDER BY c.atualizadoEm DESC")
    List<Custodiado> findArquivados();

    // Verificar duplicidade de CPF em custodiados ATIVOS
    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.cpf = :cpf AND c.statusCustodiado = 'ATIVO' AND (:id IS NULL OR c.id != :id)")
    boolean existsByCpfAndAtivoAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    // Verificar duplicidade de RG em custodiados ATIVOS
    @Query("SELECT COUNT(c) > 0 FROM Custodiado c WHERE c.rg = :rg AND c.statusCustodiado = 'ATIVO' AND (:id IS NULL OR c.id != :id)")
    boolean existsByRgAndAtivoAndIdNot(@Param("rg") String rg, @Param("id") Long id);
}