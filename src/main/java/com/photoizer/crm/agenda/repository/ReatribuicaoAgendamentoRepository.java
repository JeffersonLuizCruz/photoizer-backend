package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.ReatribuicaoAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReatribuicaoAgendamentoRepository extends JpaRepository<ReatribuicaoAgendamento, UUID> {

    @Query("""
        SELECT r FROM ReatribuicaoAgendamento r
        LEFT JOIN FETCH r.fotografoAnterior
        LEFT JOIN FETCH r.novoFotografo
        LEFT JOIN FETCH r.solicitante
        WHERE r.agendamento.id = :agendamentoId
        ORDER BY r.auditInfo.createdAt ASC
        """)
    List<ReatribuicaoAgendamento> findByAgendamentoIdWithUsuarios(@Param("agendamentoId") UUID agendamentoId);
}
