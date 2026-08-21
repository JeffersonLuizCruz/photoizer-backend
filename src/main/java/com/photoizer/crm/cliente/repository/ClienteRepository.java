package com.photoizer.crm.cliente.repository;

import com.photoizer.crm.cliente.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de clientes.
 * Estende JpaSpecificationExecutor para suportar queries Specification.
 * Padrão Specification - flexibilidade e reutilização de critérios de busca.
 */
public interface ClienteRepository extends JpaRepository<Cliente, UUID>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByTelefone(String telefone);

    Optional<Cliente> findByCpf(String cpf);

    Optional<Cliente> findByEmailIgnoreCase(String email);

    List<Cliente> findByTelefoneContaining(String telefone);

    List<Cliente> findByNomeContainingIgnoreCase(String nome);

    Page<Cliente> findByNomeContainingIgnoreCaseOrTelefoneContaining(String nome, String telefone, Pageable pageable);

    long countByDataCadastroBetween(LocalDateTime start, LocalDateTime end);
}
