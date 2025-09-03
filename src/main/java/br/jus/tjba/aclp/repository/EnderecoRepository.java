package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    Optional<Endereco> findByCep(String cep);

    List<Endereco> findByCidade(String cidade);

    List<Endereco> findByEstado(String estado);
}