package com.photoizer.crm.dashboard.api;

import java.math.BigDecimal;

public record DashboardKpisResponse(
    long agendamentosMes,
    BigDecimal receitaMes,
    double taxaConversao,
    long novosClientesMes,
    long agendamentosHoje
) {}
