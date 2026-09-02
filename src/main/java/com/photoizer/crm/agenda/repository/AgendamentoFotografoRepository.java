package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.repository.projection.RepasseAggregation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgendamentoFotografoRepository extends JpaRepository<AgendamentoFotografo, UUID> {

    List<AgendamentoFotografo> findByAgendamentoId(UUID agendamentoId);

    List<AgendamentoFotografo> findByFotografoIdOrderByAgendamentoDataHoraEnsaioDesc(UUID fotografoId);

    @Query("""
        SELECT af.agendamento.id AS agendamentoId, af.status AS status, COALESCE(SUM(af.valorRepassar), 0) AS valor
        FROM AgendamentoFotografo af
        WHERE af.status <> :statusCancelado
        GROUP BY af.agendamento.id, af.status
        """)
    List<RepasseAggregation> sumRepassesAtivosPorAgendamento(@Param("statusCancelado") RepasseStatus statusCancelado);

    @Query("SELECT af FROM AgendamentoFotografo af JOIN FETCH af.fotografo JOIN FETCH af.agendamento a JOIN FETCH a.cliente LEFT JOIN FETCH a.pacote WHERE af.fotografo.id = :fotografoId ORDER BY a.dataHoraEnsaio DESC")
    List<AgendamentoFotografo> findByFotografoIdWithAgendamento(@Param("fotografoId") UUID fotografoId);

    @Query("SELECT af FROM AgendamentoFotografo af JOIN FETCH af.fotografo WHERE af.agendamento.id = :agendamentoId")
    List<AgendamentoFotografo> findByAgendamentoIdWithFotografo(@Param("agendamentoId") UUID agendamentoId);

    @Query("SELECT af FROM AgendamentoFotografo af JOIN FETCH af.agendamento a JOIN FETCH a.cliente LEFT JOIN FETCH a.pacote WHERE af.status = :status")
    List<AgendamentoFotografo> findByStatusWithAgendamento(@Param("status") RepasseStatus status);

    @Query("SELECT af FROM AgendamentoFotografo af JOIN FETCH af.agendamento a JOIN FETCH a.cliente LEFT JOIN FETCH a.pacote WHERE af.fotografo.id = :fotografoId AND af.status = :status")
    List<AgendamentoFotografo> findByFotografoIdAndStatusWithAgendamento(@Param("fotografoId") UUID fotografoId, @Param("status") RepasseStatus status);
}
