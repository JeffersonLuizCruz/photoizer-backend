package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID>, JpaSpecificationExecutor<Agendamento> {

    boolean existsByDataHoraEnsaioBetweenAndStatusNot(
        LocalDateTime start, LocalDateTime end, StatusAgendamento statusExcluded);

    boolean existsByDataHoraEnsaioBetweenAndStatusNotAndIdNot(
        LocalDateTime start, LocalDateTime end, StatusAgendamento statusExcluded, UUID idExcluded);

    @Query("SELECT a FROM Agendamento a WHERE a.localEnsaio = :local " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findActiveByLocalAndDataBetween(
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
    List<Agendamento> findActiveBetweenExcludingId(
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados,
        @Param("excluirId") UUID excluirId);

    @Query("SELECT a FROM Agendamento a WHERE a.fotografo.id = :fotografoId " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findActiveByFotografoAndDataBetween(
        @Param("fotografoId") UUID fotografoId,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.fotografo.id = :fotografoId " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados AND a.id <> :excluirId")
    List<Agendamento> findActiveByFotografoAndDataBetweenExcludingId(
        @Param("fotografoId") UUID fotografoId,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados,
        @Param("excluirId") UUID excluirId);

    boolean existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNot(
        UUID fotografoId, LocalDateTime start, LocalDateTime end, StatusAgendamento statusExcluded);

    boolean existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNotAndIdNot(
        UUID fotografoId, LocalDateTime start, LocalDateTime end, StatusAgendamento statusExcluded, UUID idExcluded);

    @Query("SELECT a FROM Agendamento a WHERE a.cliente.id = :clienteId ORDER BY a.dataHoraEnsaio DESC")
    List<Agendamento> findByClienteId(@Param("clienteId") UUID clienteId);

    java.util.Optional<Agendamento> findByTokenGaleria(UUID tokenGaleria);

    long countByDataHoraEnsaioBetween(LocalDateTime start, LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agendamento a WHERE a.id = :id")
    Optional<Agendamento> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agendamento a " +
           "WHERE a.cliente.id = :clienteId AND a.valorRestante > 0 AND a.status <> com.photoizer.crm.agenda.model.StatusAgendamento.CANCELADO")
    boolean existsByClienteIdWithSaldoDevedor(@Param("clienteId") UUID clienteId);

}
