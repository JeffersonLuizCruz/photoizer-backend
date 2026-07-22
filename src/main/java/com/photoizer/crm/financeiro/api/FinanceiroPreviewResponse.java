package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;

public record FinanceiroPreviewResponse(
    BigDecimal valorTotal,
    BigDecimal valorEntradaExigido,
    BigDecimal valorRestante,
    BigDecimal valorTotalFinal
) {
}
