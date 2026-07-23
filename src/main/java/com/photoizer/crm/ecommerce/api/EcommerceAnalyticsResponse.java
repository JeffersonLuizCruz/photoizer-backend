package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record EcommerceAnalyticsResponse(
    int totalPedidos,
    Map<String, Integer> pedidosPorStatus,
    BigDecimal receitaTotal,
    BigDecimal receitaExtras,
    int totalFotosSelecionadas,
    int totalFotosVendidasExtras,
    double taxaConversaoExtras,
    List<FotoPopularResponse> fotosMaisSelecionadas
) {
    public record FotoPopularResponse(
        String fotoId,
        String fileName,
        String thumbUrl,
        boolean selecionadaPacote,
        boolean vendidaExtra
    ) {}
}
