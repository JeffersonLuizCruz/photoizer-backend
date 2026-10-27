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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CompraExtraRepository extends JpaRepository<CompraExtra, UUID> {

    List<CompraExtra> findByAgendamentoId(UUID agendamentoId);

    List<CompraExtra> findByStatus(StatusCompraExtra status);

    Page<CompraExtra> findByStatus(StatusCompraExtra status, Pageable pageable);

    @Query("SELECT c FROM CompraExtra c WHERE c.auditInfo.createdAt BETWEEN :dataInicio AND :dataFim")
    Page<CompraExtra> findByPeriodo(@Param("dataInicio") LocalDateTime dataInicio,
                                    @Param("dataFim") LocalDateTime dataFim,
                                    Pageable pageable);

    @Query("SELECT c FROM CompraExtra c WHERE c.status = :status AND c.auditInfo.createdAt BETWEEN :dataInicio AND :dataFim")
    Page<CompraExtra> findByStatusAndPeriodo(@Param("status") StatusCompraExtra status,
                                              @Param("dataInicio") LocalDateTime dataInicio,
                                              @Param("dataFim") LocalDateTime dataFim,
                                              Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM CompraExtra c WHERE c.status = :status")
    BigDecimal totalPorStatus(@Param("status") StatusCompraExtra status);

    int countByStatus(StatusCompraExtra status);

    @Query("SELECT c.urlComprovante FROM CompraExtra c WHERE c.urlComprovante IS NOT NULL")
    List<String> caminhosComprovante();

    interface CompraPipelineAgregado {
        UUID getAgendamentoId();

        long getPendentes();

        BigDecimal getValorPago();
    }

    /**
     * Compras pendentes e valor pago por galeria, para os badges da Central de
     * Galerias. Pendente = aguardando comprovante ou confirmacao do staff.
     */
    @Query("""
        SELECT c.agendamentoId AS agendamentoId,
               SUM(CASE WHEN c.status IN :statusesPendentes THEN 1 ELSE 0 END) AS pendentes,
               COALESCE(SUM(CASE WHEN c.status = 'PAGA' THEN c.valorTotal ELSE 0 END), 0) AS valorPago
        FROM CompraExtra c
        WHERE c.agendamentoId IN :agendamentoIds
        GROUP BY c.agendamentoId
        """)
    List<CompraPipelineAgregado> agregarPipeline(
        @Param("agendamentoIds") Collection<UUID> agendamentoIds,
        @Param("statusesPendentes") Collection<StatusCompraExtra> statusesPendentes);
}
