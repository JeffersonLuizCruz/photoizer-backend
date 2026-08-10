package com.photoizer.crm.despesa.repository;

import com.photoizer.crm.despesa.model.DespesaCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DespesaCategoriaRepository extends JpaRepository<DespesaCategoria, UUID> {

    List<DespesaCategoria> findByAtivoTrueOrderByOrdemAscNomeAsc();

    Optional<DespesaCategoria> findByNomeIgnoreCase(String nome);
}
