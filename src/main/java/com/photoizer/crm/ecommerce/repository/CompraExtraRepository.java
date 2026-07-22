package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.CompraExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompraExtraRepository extends JpaRepository<CompraExtra, UUID> {
}
