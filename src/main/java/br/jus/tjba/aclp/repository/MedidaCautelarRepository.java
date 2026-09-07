package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.MedidaCautelar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedidaCautelarRepository extends JpaRepository<MedidaCautelar, Long> {

    List<MedidaCautelar> findByProcessoIdOrderByDataImposicaoDesc(Long processoId);
}
