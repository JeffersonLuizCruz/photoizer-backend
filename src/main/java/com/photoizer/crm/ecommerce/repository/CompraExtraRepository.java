package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CompraExtraRepository extends JpaRepository<CompraExtra, UUID> {

    List<CompraExtra> findByAgendamentoId(UUID agendamentoId);

    List<CompraExtra> findByStatus(StatusCompraExtra status);

    Page<CompraExtra> findByStatus(StatusCompraExtra status, Pageable pageable);

    @Query("SELECT c FROM CompraExtra c WHERE c.createdAt BETWEEN :dataInicio AND :dataFim")
    Page<CompraExtra> findByPeriodo(@Param("dataInicio") LocalDateTime dataInicio,
                                    @Param("dataFim") LocalDateTime dataFim,
                                    Pageable pageable);

    @Query("SELECT c FROM CompraExtra c WHERE c.status = :status AND c.createdAt BETWEEN :dataInicio AND :dataFim")
    Page<CompraExtra> findByStatusAndPeriodo(@Param("status") StatusCompraExtra status,
                                              @Param("dataInicio") LocalDateTime dataInicio,
                                              @Param("dataFim") LocalDateTime dataFim,
                                              Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM CompraExtra c WHERE c.status = :status")
    BigDecimal totalPorStatus(@Param("status") StatusCompraExtra status);

    int countByStatus(StatusCompraExtra status);
}
