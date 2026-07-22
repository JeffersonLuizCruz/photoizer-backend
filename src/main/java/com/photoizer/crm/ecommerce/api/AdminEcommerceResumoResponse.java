package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AdminEcommerceResumoResponse(
    int totalFotos,
    int fotosPublicadas,
    int fotosSelecionadasPacote,
    int fotosPagas,
    int fotosAguardando,
    List<FotoEnsaioResponse> fotos,
    List<CompraExtraResponse> comprasExtras,
    BigDecimal valorTotalExtras,
    String linkGaleria,
    UUID tokenGaleria
) {}
