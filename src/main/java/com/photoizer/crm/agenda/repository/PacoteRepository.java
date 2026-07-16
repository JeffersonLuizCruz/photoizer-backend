package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Pacote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PacoteRepository extends JpaRepository<Pacote, UUID> {
}
