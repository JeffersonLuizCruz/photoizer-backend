package com.photoizer.crm.despesa.service;

import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Facade de leitura para dados de despesas.
 * Pattern: Query Service Facade — agrupa despesas por mês no módulo dono,
 * evitando que dashboard/financeiro carreguem todas as despesas em memória.
 */
@Service
@Transactional(readOnly = true)
public class DespesaQueryService {

    private final DespesaRepository despesaRepository;

    public DespesaQueryService(DespesaRepository despesaRepository) {
        this.despesaRepository = despesaRepository;
    }

    /**
     * Retorna despesas totais e pagas agrupadas por YearMonth no período informado.
     */
    public DespesasPorPeriodo obterPorPeriodo(LocalDate inicio, LocalDate fim) {
        var despesas = despesaRepository.findByDataBetweenOrderByDataDesc(inicio, fim);
        Map<YearMonth, BigDecimal> totalPorMes = new HashMap<>();
        Map<YearMonth, BigDecimal> pagasPorMes = new HashMap<>();

        for (var d : despesas) {
            var ym = YearMonth.from(d.getData());
            totalPorMes.merge(ym, d.getValor(), BigDecimal::add);
            if (d.getStatus() == StatusDespesa.PAGO) {
                pagasPorMes.merge(ym, d.getValor(), BigDecimal::add);
            }
        }
        return new DespesasPorPeriodo(totalPorMes, pagasPorMes);
    }

    public record DespesasPorPeriodo(
        Map<YearMonth, BigDecimal> totalPorMes,
        Map<YearMonth, BigDecimal> pagasPorMes
    ) {}
}
