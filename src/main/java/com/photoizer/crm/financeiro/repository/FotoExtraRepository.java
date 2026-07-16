package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.FotoExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FotoExtraRepository extends JpaRepository<FotoExtra, UUID> {
}
