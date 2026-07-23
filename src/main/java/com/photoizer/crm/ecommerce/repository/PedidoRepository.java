package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    List<Pedido> findByClienteIdOrderByCreatedAtDesc(UUID clienteId);

    Optional<Pedido> findByTokenGaleria(UUID token);

    Page<Pedido> findByStatus(String status, Pageable pageable);

    int countByStatus(String status);
}
