package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;

public record FinanceiroResumoResponse(
    BigDecimal totalEntradas,
    BigDecimal totalFinal,
    BigDecimal totalExtras,
    BigDecimal faturamentoTotal,
    BigDecimal despesasDeslocamento,
    BigDecimal despesasComissao,
    BigDecimal despesasManuais
) {
}
