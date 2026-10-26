package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.GaleriaEnsaio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GaleriaEnsaioRepository extends JpaRepository<GaleriaEnsaio, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GaleriaEnsaio g WHERE g.agendamentoId = :agendamentoId")
    Optional<GaleriaEnsaio> findByIdForUpdate(@Param("agendamentoId") UUID agendamentoId);
}
