package com.photoizer.crm.bloqueio.repository;

import com.photoizer.crm.bloqueio.model.BloqueioAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, UUID> {

    Optional<BloqueioAgenda> findByIdAndAtivoTrue(UUID id);

    @Query("SELECT b FROM BloqueioAgenda b WHERE b.ativo = true " +
           "AND b.inicio < :fim AND b.fim > :inicio ORDER BY b.inicio")
    List<BloqueioAgenda> findAtivosNoPeriodo(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim);

    @Query("SELECT b FROM BloqueioAgenda b WHERE b.ativo = true " +
           "AND b.inicio < :fim AND b.fim > :inicio " +
           "AND (b.profissionalId IS NULL " +
           "     OR (:profissionalId IS NOT NULL AND b.profissionalId = :profissionalId)) " +
           "ORDER BY b.inicio")
    List<BloqueioAgenda> findAtivosAplicaveisNoPeriodo(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim,
        @Param("profissionalId") UUID profissionalId);
}
