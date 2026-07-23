package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {
}
