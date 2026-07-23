package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    List<Avaliacao> findByAprovadoTrue();

    List<Avaliacao> findByClienteId(UUID clienteId);
}
