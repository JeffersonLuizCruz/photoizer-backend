package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.RascunhoAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RascunhoAgendamentoRepository extends JpaRepository<RascunhoAgendamento, UUID> {

    Optional<RascunhoAgendamento> findByUsuarioId(UUID usuarioId);

    void deleteByUsuarioId(UUID usuarioId);
}
