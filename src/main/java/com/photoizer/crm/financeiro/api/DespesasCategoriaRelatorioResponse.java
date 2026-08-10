package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.util.List;

public record DespesasCategoriaRelatorioResponse(
    BigDecimal total,
    List<Item> categorias
) {
    public record Item(
        String categoria,
        String cor,
        BigDecimal valor,
        long qtd,
        BigDecimal percentual
    ) {}
}
