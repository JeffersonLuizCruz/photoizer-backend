package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.Cupom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CupomRepository extends JpaRepository<Cupom, UUID> {

    Optional<Cupom> findByCodigoIgnoreCase(String codigo);
}
