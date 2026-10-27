package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Indicadores de gestão da Central de Galerias: SLA de publicação/edição,
 * funil de galerias, extras e série mensal.
 */
public record GaleriaPerformanceResponse(
    int meses,
    int totalGalerias,
    int publicadas,
    int comSelecao,
    int comercializadas,
    int comExtras,
    int atrasadas,
    Integer tempoMedioPublicacaoDias,
    Integer tempoMedioEdicaoDias,
    Integer tempoMedioEntregaDias,
    BigDecimal receitaExtras,
    BigDecimal ticketMedioExtras,
    BigDecimal extrasPorGaleria,
    double conversaoExtras,
    int totalFotosExtras,
    int totalComprasPagas,
    List<SerieMensalResponse> serieMensal
) {
    public record SerieMensalResponse(
        String mes,
        int galerias,
        int publicadas,
        int comExtras,
        BigDecimal receitaExtras,
        Integer tempoMedioPublicacaoDias
    ) {
    }
}
