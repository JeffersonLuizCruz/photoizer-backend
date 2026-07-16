package com.photoizer.crm.cliente.repository;

import com.photoizer.crm.cliente.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByTelefone(String telefone);

    List<Cliente> findByTelefoneContaining(String telefone);

    List<Cliente> findByNomeContainingIgnoreCase(String nome);
}
