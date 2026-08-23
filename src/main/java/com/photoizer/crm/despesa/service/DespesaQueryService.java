package com.photoizer.crm.despesa.service;

/*
 * REFACTORED — DespesaQueryService (SQL GROUP BY)
 *
 * Design Pattern: Query Service Facade
 *
 * Antes: obterPorPeriodo() carregava todas as despesas do período via
 * findByDataBetweenOrderByDataDesc() e agregava em memória com HashMap.
 * Com volume crescente de despesas, isso causava OOM (DEBT.md §3 —
 * "agregação em memória" listado como padrão transversal a resolver).
 *
 * Agora: usa SUM + CASE WHEN diretamente no banco via sumByMesBetween(),
 * retornando a projeção DespesaPorMesProjection. A agregação é feita
 * pelo SQL GROUP BY YEAR(data), MONTH(data), eliminando o processamento
 * em memória e reduzindo a transferência de dados.
 */

import com.photoizer.crm.despesa.repository.DespesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DespesaQueryService {

    private final DespesaRepository despesaRepository;

    public DespesaQueryService(DespesaRepository despesaRepository) {
        this.despesaRepository = despesaRepository;
    }

    /**
     * Retorna despesas totais e pagas agrupadas por YearMonth no período informado.
     * Utiliza SQL GROUP BY para agregação no banco, evitando processamento em memória.
     */
    public DespesasPorPeriodo obterPorPeriodo(LocalDate inicio, LocalDate fim) {
        var projetoes = despesaRepository.sumByMesBetween(inicio, fim);
        Map<YearMonth, BigDecimal> totalPorMes = new HashMap<>();
        Map<YearMonth, BigDecimal> pagasPorMes = new HashMap<>();

        for (var p : projetoes) {
            var ym = YearMonth.of(p.ano(), p.mes());
            totalPorMes.put(ym, p.total());
            pagasPorMes.put(ym, p.pagas());
        }
        return new DespesasPorPeriodo(totalPorMes, pagasPorMes);
    }

    public record DespesasPorPeriodo(
        Map<YearMonth, BigDecimal> totalPorMes,
        Map<YearMonth, BigDecimal> pagasPorMes
    ) {}
}
