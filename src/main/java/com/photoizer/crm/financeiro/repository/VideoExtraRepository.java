package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.VideoExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VideoExtraRepository extends JpaRepository<VideoExtra, UUID> {

    List<VideoExtra> findByAgendamentoId(UUID agendamentoId);
}
