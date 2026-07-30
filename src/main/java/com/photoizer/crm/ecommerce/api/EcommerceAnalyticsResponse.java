package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;
import java.util.List;

public record EcommerceAnalyticsResponse(
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
