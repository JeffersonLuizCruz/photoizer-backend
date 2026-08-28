package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.util.List;

public record FinanceiroRelatoriosResponse(
    RelatoriosTotais totais,
    List<RelatorioAgendamentoItem> agendamentos,
    int quantidade
) {
    public record RelatoriosTotais(
        BigDecimal total,
        BigDecimal entrada,
        BigDecimal restante,
        BigDecimal extras,
        BigDecimal totalFinal,
        BigDecimal repasses,
        BigDecimal comissao
    ) {}
}
