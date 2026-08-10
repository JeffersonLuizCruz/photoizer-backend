package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumoMensalResponse(
    LocalDate inicio,
    LocalDate fim,
    BigDecimal receitasBrutas,
    BigDecimal receitasRecebidas,
    BigDecimal aReceber,
    BigDecimal despesasTotal,
    BigDecimal despesasPagas,
    BigDecimal aPagar,
    BigDecimal lucroPrevisto,
    BigDecimal lucroRealizado,
    BigDecimal margemLucro,
    long qtdReceitas,
    long qtdDespesas
) {}
