package com.photoizer.crm.despesa.repository;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DespesaRepository extends JpaRepository<Despesa, UUID>, JpaSpecificationExecutor<Despesa> {

    List<Despesa> findByDataBetweenOrderByDataDesc(LocalDate inicio, LocalDate fim);

    List<Despesa> findAllByOrderByDataDesc();

    List<Despesa> findByAgendamentoIdOrderByDataDesc(UUID agendamentoId);

    List<Despesa> findByStatusAndDataProximaGeracaoNotNullAndDataProximaGeracaoLessThanEqual(
        StatusDespesa status, LocalDate dataLimite);

    long countByCategoriaRefId(UUID categoriaId);

    List<Despesa> findByFotografoIdOrderByDataDesc(UUID fotografoId);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.agendamentoId = :agendamentoId AND d.fotografoId = :fotografoId")
    BigDecimal sumValorByAgendamentoIdAndFotografoId(
        @Param("agendamentoId") UUID agendamentoId,
        @Param("fotografoId") UUID fotografoId);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.agendamentoId = :agendamentoId AND d.fotografoId IS NOT NULL")
    BigDecimal sumValorByAgendamentoIdWithFotografo(@Param("agendamentoId") UUID agendamentoId);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.data BETWEEN :inicio AND :fim")
    BigDecimal sumValorByDataBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Projeção para agregação de despesas por mês.
     * Usada pelo DespesaQueryService para evitar agregação em memória.
     */
    record DespesaPorMesProjection(
        int ano,
        int mes,
        BigDecimal total,
        BigDecimal pagas
    ) {}

    @Query("""
        SELECT new com.photoizer.crm.despesa.repository.DespesaRepository$DespesaPorMesProjection(
            YEAR(d.data),
            MONTH(d.data),
            COALESCE(SUM(d.valor), 0),
            COALESCE(SUM(CASE WHEN d.status = com.photoizer.crm.despesa.model.StatusDespesa.PAGO THEN d.valor ELSE 0 END), 0)
        )
        FROM Despesa d
        WHERE d.data BETWEEN :inicio AND :fim
        GROUP BY YEAR(d.data), MONTH(d.data)
        ORDER BY YEAR(d.data) DESC, MONTH(d.data) DESC
        """)
    List<DespesaPorMesProjection> sumByMesBetween(
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.data BETWEEN :inicio AND :fim AND d.status = :status")
    BigDecimal sumValorByDataBetweenAndStatus(
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim,
        @Param("status") StatusDespesa status);

    @Query("SELECT d.categoria FROM Despesa d WHERE d.data BETWEEN :inicio AND :fim GROUP BY d.categoria ORDER BY SUM(d.valor) DESC")
    List<String> findCategoriasByDataBetween(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.data BETWEEN :inicio AND :fim AND d.categoria = :categoria")
    BigDecimal sumValorByDataBetweenAndCategoria(
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim,
        @Param("categoria") String categoria);

    List<Despesa> findByStatusAndDataBetween(@Param("status") StatusDespesa status, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
