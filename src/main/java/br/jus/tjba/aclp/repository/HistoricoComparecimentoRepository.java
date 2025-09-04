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

    List<HistoricoComparecimento> findByPessoaOrderByDataComparecimentoDesc(Pessoa pessoa);

    List<HistoricoComparecimento> findByDataComparecimento(LocalDate data);

    List<HistoricoComparecimento> findByTipoValidacao(TipoValidacao tipo);

    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.pessoa.id = :pessoaId ORDER BY h.dataComparecimento DESC")
    List<HistoricoComparecimento> findByPessoaIdOrderByDataComparecimentoDesc(@Param("pessoaId") Long pessoaId);

    @Query("SELECT h FROM HistoricoComparecimento h WHERE h.dataComparecimento BETWEEN :inicio AND :fim")
    List<HistoricoComparecimento> findByDataComparecimentoBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    long countByPessoa(Pessoa pessoa);
}