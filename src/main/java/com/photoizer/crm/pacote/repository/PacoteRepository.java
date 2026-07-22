package com.photoizer.crm.pacote.repository;

import com.photoizer.crm.pacote.model.Pacote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PacoteRepository extends JpaRepository<Pacote, UUID> {

    Page<Pacote> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}
