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
}
