package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

    Optional<Assinatura> findByAgendamentoId(UUID agendamentoId);
}
