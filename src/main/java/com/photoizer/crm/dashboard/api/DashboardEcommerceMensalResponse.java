package com.photoizer.crm.dashboard.api;

import java.math.BigDecimal;
import java.util.List;

public record DashboardEcommerceMensalResponse(
    List<DadosEcommerceMensal> historico
) {
    public record DadosEcommerceMensal(
        String mes,
        int quantidadeCompras,
        int quantidadeFotos,
        BigDecimal valorTotal
    ) {}
}
