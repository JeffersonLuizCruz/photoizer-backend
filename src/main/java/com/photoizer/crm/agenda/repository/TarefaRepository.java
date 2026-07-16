package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Tarefa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TarefaRepository extends JpaRepository<Tarefa, UUID> {

    List<Tarefa> findByAgendamentoIdOrderByDataLimiteAsc(UUID agendamentoId);
}
