package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID>, JpaSpecificationExecutor<Agendamento> {

    boolean existsByDataHoraEnsaioBetweenAndStatusNot(
        LocalDateTime start, LocalDateTime end, StatusAgendamento statusExcluded);

    boolean existsByDataHoraEnsaioBetweenAndStatusNotAndIdNot(
        LocalDateTime start, LocalDateTime end, StatusAgendamento statusExcluded, UUID idExcluded);

    @Query("SELECT a FROM Agendamento a WHERE a.localEnsaio = :local " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findByLocalAndDataBetween(
        @Param("local") String local,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findByDataBetween(
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados AND a.id <> :excluirId")
    List<Agendamento> findByLocalAndDataBetweenExcludingId(
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados,
        @Param("excluirId") UUID excluirId);
}
