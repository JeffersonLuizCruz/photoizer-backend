package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReceitaRepository extends JpaRepository<Receita, UUID>, JpaSpecificationExecutor<Receita> {

    List<Receita> findByAgendamentoIdOrderByDataPrevisaoRecebimentoDesc(UUID agendamentoId);

    @Query("SELECT r FROM Receita r WHERE r.status IN :statuses AND r.dataPrevisaoRecebimento < :dataLimite")
    List<Receita> findInadimplentes(
        @Param("statuses") Collection<StatusReceita> statuses,
        @Param("dataLimite") LocalDate dataLimite);

    @Query("SELECT r FROM Receita r WHERE r.agendamentoId IS NULL AND r.status <> com.photoizer.crm.financeiro.model.StatusReceita.CANCELADO " +
           "AND r.dataPrevisaoRecebimento BETWEEN :inicio AND :fim")
    List<Receita> findAvulsasByDataBetween(
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);

    @Query("SELECT r FROM Receita r WHERE r.agendamentoId IS NULL AND r.status <> com.photoizer.crm.financeiro.model.StatusReceita.CANCELADO " +
           "AND r.dataRecebimentoReal IS NOT NULL AND r.dataRecebimentoReal BETWEEN :inicio AND :fim")
    List<Receita> findAvulsasRecebidasByDataRecebimentoBetween(
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim);

    @Query("SELECT r FROM Receita r WHERE r.status IN :statuses AND r.dataPrevisaoRecebimento BETWEEN :inicio AND :fim")
    List<Receita> findByStatusInAndDataPrevisaoRecebimentoBetween(
        @Param("statuses") Collection<StatusReceita> statuses,
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);

    @Query("SELECT COALESCE(SUM(r.valorBruto), 0) FROM Receita r WHERE r.status <> com.photoizer.crm.financeiro.model.StatusReceita.CANCELADO " +
           "AND r.dataPrevisaoRecebimento BETWEEN :inicio AND :fim")
    BigDecimal sumValorBrutoByDataPrevisaoRecebimentoBetween(
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim);

    @Query("SELECT COALESCE(SUM(r.valorBruto), 0) FROM Receita r WHERE r.status <> com.photoizer.crm.financeiro.model.StatusReceita.CANCELADO " +
           "AND r.dataPrevisaoRecebimento BETWEEN :inicio AND :fim AND r.tipoServico = :tipoServico")
    BigDecimal sumValorBrutoByDataPrevisaoRecebimentoBetweenAndTipoServico(
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim,
        @Param("tipoServico") com.photoizer.crm.financeiro.model.TipoServico tipoServico);
}
