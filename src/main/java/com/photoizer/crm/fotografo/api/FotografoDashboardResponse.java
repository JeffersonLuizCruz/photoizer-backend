package com.photoizer.crm.fotografo.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FotografoDashboardResponse(
    UUID fotografoId,
    String fotografoNome,
    int totalEnsaios,
    BigDecimal totalValorCobrado,
    BigDecimal totalCustosFotografo,
    BigDecimal totalPartilha,
    BigDecimal totalRepasse,
    BigDecimal totalLucroCrm,
    List<FotografoEnsaiosResponse> ultimosEnsaios
) {}