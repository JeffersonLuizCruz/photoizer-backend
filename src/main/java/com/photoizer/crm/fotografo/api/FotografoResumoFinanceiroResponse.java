package com.photoizer.crm.fotografo.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FotografoResumoFinanceiroResponse(
    UUID fotografoId,
    String fotografoNome,
    int totalEnsaios,
    int ensaiosPendentes,
    int ensaiosRealizados,
    int ensaiosFinalizados,
    BigDecimal totalValorCobrado,
    BigDecimal totalCustosFotografo,
    BigDecimal totalPartilha,
    BigDecimal totalRepasse,
    BigDecimal totalLucroCrm,
    BigDecimal mediaPartilhaPorEnsaio,
    BigDecimal totalRepassesPendentes,
    BigDecimal totalRepassesRealizados,
    Map<String, BigDecimal> custosPorCategoria,
    List<CustoPorEnsaio> custosPorEnsaio
) {
    public record CustoPorEnsaio(
        UUID agendamentoId,
        String clienteNome,
        String dataEnsaio,
        BigDecimal total
    ) {}
}