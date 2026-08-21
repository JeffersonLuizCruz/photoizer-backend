package com.photoizer.crm.comissao.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response do endpoint GET /comissoes/indicadores.
 * Substitui o Map<String,Object> por record tipado com compile-time safety.
 */
public record IndicadorResumoResponse(
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    BigDecimal totalPendente,
    BigDecimal totalPago,
    BigDecimal totalCancelado,
    int totalIndicacoes,
    BigDecimal percentualComissao
) {}
