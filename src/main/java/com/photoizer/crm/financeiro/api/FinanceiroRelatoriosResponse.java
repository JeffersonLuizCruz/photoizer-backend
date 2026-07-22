package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.agenda.api.AgendamentoResponse;

import java.math.BigDecimal;
import java.util.List;

public record FinanceiroRelatoriosResponse(
    RelatoriosTotais totais,
    List<AgendamentoResponse> agendamentos,
    int quantidade
) {
    public record RelatoriosTotais(
        BigDecimal total,
        BigDecimal entrada,
        BigDecimal restante,
        BigDecimal extras,
        BigDecimal totalFinal
    ) {}
}
