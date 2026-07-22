package com.photoizer.crm.foto.repository;

import com.photoizer.crm.foto.model.FotoEnsaio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FotoEnsaioRepository extends JpaRepository<FotoEnsaio, UUID> {

    List<FotoEnsaio> findByAgendamentoIdOrderByOrdemAsc(UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndStatusOrderByOrdemAsc(UUID agendamentoId, String status);

    int countByAgendamentoId(UUID agendamentoId);
}
