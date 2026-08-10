package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioFiscalResponse(
    LocalDate inicio,
    LocalDate fim,
    BigDecimal totalReceitas,
    BigDecimal totalComissoes,
    BigDecimal totalDespesas,
    BigDecimal lucroLiquido,
    long qtdReceitas,
    long qtdDespesas,
    List<FinanceiroDashboardResponse.DespesaCategoriaDado> despesasPorCategoria
) {}
