package com.photoizer.crm.dashboard.api;

import java.math.BigDecimal;
import java.util.List;

public record DashboardEcommerceResponse(
    int totalCompras,
    int totalFotosExtras,
    BigDecimal totalFaturado,
    BigDecimal ticketMedio,
    List<TopCliente> topClientes
) {
    public record TopCliente(
        String nomeCliente,
        String telefoneCliente,
        int quantidadeCompras,
        BigDecimal totalGasto
    ) {}
}
