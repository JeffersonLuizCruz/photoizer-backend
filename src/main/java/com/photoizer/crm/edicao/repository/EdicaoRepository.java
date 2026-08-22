package com.photoizer.crm.edicao.repository;

import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.StatusEdicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdicaoRepository extends JpaRepository<Edicao, UUID> {

    Optional<Edicao> findByAgendamentoId(UUID agendamentoId);

    List<Edicao> findByStatusOrderByAuditInfoUpdatedAtDesc(StatusEdicao status);

    List<Edicao> findAllByOrderByAuditInfoUpdatedAtDesc();

    boolean existsByAgendamentoId(UUID agendamentoId);
}
