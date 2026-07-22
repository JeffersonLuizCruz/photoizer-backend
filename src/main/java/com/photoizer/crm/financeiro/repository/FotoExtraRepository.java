package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.FotoExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FotoExtraRepository extends JpaRepository<FotoExtra, UUID> {

    List<FotoExtra> findByAgendamentoId(UUID agendamentoId);
}
