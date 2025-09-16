package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Custodiado;
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

    // Busca por processo - pode retornar múltiplos custodiados
    List<Custodiado> findByProcesso(String processo);

    // Busca por processo e nome para identificar custodiado específico
    Optional<Custodiado> findByProcessoAndNomeContainingIgnoreCase(String processo, String nome);

    Optional<Custodiado> findByCpf(String cpf);

    Optional<Custodiado> findByRg(String rg);

    List<Custodiado> findByStatus(StatusComparecimento status);

    List<Custodiado> findByProximoComparecimento(LocalDate data);

    // Busca inadimplentes (próximo comparecimento anterior a hoje)
    List<Custodiado> findByProximoComparecimentoBefore(LocalDate data);

    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento BETWEEN :inicio AND :fim")
    List<Custodiado> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT c FROM Custodiado c WHERE c.nome LIKE %:nome% OR c.processo LIKE %:processo%")
    List<Custodiado> buscarPorNomeOuProcesso(@Param("nome") String nome, @Param("processo") String processo);

    long countByStatus(StatusComparecimento status);

    // Busca todos os custodiados de um mesmo processo
    @Query("SELECT c FROM Custodiado c WHERE c.processo = :processo ORDER BY c.nome")
    List<Custodiado> findAllByProcesso(@Param("processo") String processo);

    // Busca custodiados com comparecimento hoje
    @Query("SELECT c FROM Custodiado c WHERE c.proximoComparecimento = CURRENT_DATE")
    List<Custodiado> findComparecimentosHoje();

    // Busca custodiados inadimplentes (método unificado)
    @Query("SELECT c FROM Custodiado c WHERE c.status = 'INADIMPLENTE' OR c.proximoComparecimento < CURRENT_DATE")
    List<Custodiado> findInadimplentes();

    // Conta custodiados por processo
    @Query("SELECT c.processo, COUNT(c) FROM Custodiado c GROUP BY c.processo HAVING COUNT(c) > 1")
    List<Object[]> findProcessosComMultiplosCustodiados();

    // Busca custodiados sem histórico de comparecimento
    @Query("SELECT c FROM Custodiado c WHERE c.id NOT IN " +
            "(SELECT DISTINCT h.custodiado.id FROM HistoricoComparecimento h)")
    List<Custodiado> findCustodiadosSemHistorico();

    // Busca custodiados por comarca
    List<Custodiado> findByComarca(String comarca);

    // Busca custodiados por vara
    List<Custodiado> findByVara(String vara);
}