package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PessoaRepository extends JpaRepository<Pessoa, Long> {

    Optional<Pessoa> findByProcesso(String processo);

    Optional<Pessoa> findByCpf(String cpf);

    Optional<Pessoa> findByRg(String rg);

    List<Pessoa> findByStatus(StatusComparecimento status);

    List<Pessoa> findByProximoComparecimento(LocalDate data);

    List<Pessoa> findByProximoComparecimentoBefore(LocalDate data);

    @Query("SELECT p FROM Pessoa p WHERE p.proximoComparecimento BETWEEN :inicio AND :fim")
    List<Pessoa> findByProximoComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT p FROM Pessoa p WHERE p.nome LIKE %:nome% OR p.processo LIKE %:processo%")
    List<Pessoa> buscarPorNomeOuProcesso(@Param("nome") String nome, @Param("processo") String processo);

    long countByStatus(StatusComparecimento status);
}