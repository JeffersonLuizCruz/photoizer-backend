package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FluxoCaixaResponse(
    LocalDate inicio,
    LocalDate fim,
    String visao,
    BigDecimal entradasRealizadas,
    BigDecimal saidasRealizadas,
    BigDecimal entradasPrevistasTotal,
    BigDecimal saidasPrevistasTotal,
    BigDecimal saldoProjetadoFinal,
    List<FluxoCaixaBucket> buckets,
    List<FluxoCaixaItem> itens
) {
    public record FluxoCaixaBucket(
        String rotulo,
        LocalDate inicio,
        LocalDate fim,
        BigDecimal entradasPrevistas,
        BigDecimal saidasPrevistas,
        BigDecimal saldoPeriodo,
        BigDecimal saldoAcumulado,
        BigDecimal entradasRealizadas,
        BigDecimal saidasRealizadas
    ) {}

    public record FluxoCaixaItem(
        UUID id,
        String tipo,
        String descricao,
        String categoria,
        LocalDate data,
        BigDecimal valor,
        String status,
        String origem
    ) {}
}
