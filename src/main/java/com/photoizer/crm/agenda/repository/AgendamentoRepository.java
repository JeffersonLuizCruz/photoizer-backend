package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

    @Query("SELECT a FROM Agendamento a WHERE a.cliente.id = :clienteId ORDER BY a.dataHoraEnsaio DESC")
    List<Agendamento> findByClienteId(@Param("clienteId") UUID clienteId);

    java.util.Optional<Agendamento> findByTokenGaleria(UUID tokenGaleria);

    @Query("SELECT a FROM Agendamento a JOIN FETCH a.cliente LEFT JOIN FETCH a.pacote WHERE EXISTS (SELECT 1 FROM AgendamentoFotografo af WHERE af.agendamento = a AND af.fotografo.id = :fotografoId) ORDER BY a.dataHoraEnsaio DESC")
    List<Agendamento> findByFotografoId(@Param("fotografoId") UUID fotografoId);

    long countByDataHoraEnsaioBetween(LocalDateTime start, LocalDateTime end);

}
