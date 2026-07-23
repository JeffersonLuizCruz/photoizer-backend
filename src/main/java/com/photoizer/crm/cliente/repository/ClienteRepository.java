package com.photoizer.crm.cliente.repository;

import com.photoizer.crm.cliente.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByTelefone(String telefone);

    Optional<Cliente> findByEmailIgnoreCase(String email);

    List<Cliente> findByTelefoneContaining(String telefone);

    List<Cliente> findByNomeContainingIgnoreCase(String nome);

    Page<Cliente> findByNomeContainingIgnoreCaseOrTelefoneContaining(String nome, String telefone, Pageable pageable);

    long countByDataCadastroBetween(LocalDateTime start, LocalDateTime end);
}
