package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RentabilidadeClienteResponse(
    List<Item> clientes
) {
    public record Item(
        UUID clienteId,
        String clienteNome,
        BigDecimal receitaBruta,
        BigDecimal receitaLiquida,
        BigDecimal recebido,
        BigDecimal aReceber,
        long qtdReceitas,
        BigDecimal margem
    ) {}
}
