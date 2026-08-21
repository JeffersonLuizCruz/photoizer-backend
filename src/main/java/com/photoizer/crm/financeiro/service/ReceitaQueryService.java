package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Facade de leitura para dados de receitas avulsas.
 * Pattern: Query Service Facade — centraliza queries de receitas no módulo financeiro,
 * evitando que dashboard acesse ReceitaRepository diretamente com findAll().
 */
@Service
@Transactional(readOnly = true)
public class ReceitaQueryService {

    private final ReceitaRepository receitaRepository;

    public ReceitaQueryService(ReceitaRepository receitaRepository) {
        this.receitaRepository = receitaRepository;
    }

    /**
     * Retorna receitas avulsas (sem agendamento vinculado) agrupadas por mês.
     * Bruto = soma de valorBruto por dataPrevisaoRecebimento.
     * Recebido = soma de valorRecebido por dataRecebimentoReal.
     * Exclui receitas CANCELADAS.
     */
    public ReceitasAvulsasPorMes obterAvulsasPorPeriodo(LocalDate inicio, LocalDate fim) {
        var receitas = receitaRepository.findAll();
        Map<YearMonth, BigDecimal> brutoPorMes = new HashMap<>();
        Map<YearMonth, BigDecimal> recebidoPorMes = new HashMap<>();

        for (var r : receitas) {
            if (r.getAgendamentoId() != null) continue;
            if (r.getStatus() == StatusReceita.CANCELADO) continue;

            if (r.getDataPrevisaoRecebimento() != null
                && !r.getDataPrevisaoRecebimento().isBefore(inicio)
                && !r.getDataPrevisaoRecebimento().isAfter(fim)) {
                brutoPorMes.merge(YearMonth.from(r.getDataPrevisaoRecebimento()),
                    r.getValorBruto(), BigDecimal::add);
            }
            if (r.getDataRecebimentoReal() != null) {
                var dataRecebimento = r.getDataRecebimentoReal().toLocalDate();
                if (!dataRecebimento.isBefore(inicio) && !dataRecebimento.isAfter(fim)) {
                    recebidoPorMes.merge(YearMonth.from(dataRecebimento),
                        r.getValorRecebido(), BigDecimal::add);
                }
            }
        }
        return new ReceitasAvulsasPorMes(brutoPorMes, recebidoPorMes);
    }

    public record ReceitasAvulsasPorMes(
        Map<YearMonth, BigDecimal> brutoPorMes,
        Map<YearMonth, BigDecimal> recebidoPorMes
    ) {}
}
