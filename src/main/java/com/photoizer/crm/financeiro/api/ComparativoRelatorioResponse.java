package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.util.List;

public record ComparativoRelatorioResponse(
    String tipo,
    List<Item> periodos
) {
    public record Item(
        String periodo,
        BigDecimal receitas,
        BigDecimal despesas,
        BigDecimal lucro,
        BigDecimal variacao
    ) {}
}
