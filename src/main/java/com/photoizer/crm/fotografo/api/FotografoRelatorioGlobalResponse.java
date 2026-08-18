package com.photoizer.crm.fotografo.api;

import java.math.BigDecimal;
import java.util.List;

public record FotografoRelatorioGlobalResponse(
    int totalFotografos,
    int totalEnsaios,
    BigDecimal totalValorCobrado,
    BigDecimal totalCustos,
    BigDecimal totalPartilha,
    BigDecimal totalRepasse,
    BigDecimal totalLucroCrm,
    List<FotografoItem> porFotografo
) {
    public record FotografoItem(
        String fotografoNome,
        int totalEnsaios,
        BigDecimal totalValorCobrado,
        BigDecimal totalCustos,
        BigDecimal totalPartilha,
        BigDecimal totalRepasse,
        BigDecimal totalLucroCrm
    ) {}
}