package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.util.List;

public record GaleriaResponse(
    List<FotoEnsaioResponse> fotos,
    int pacoteQuantidadeFotos,
    BigDecimal valorUnitarioFotoExtra
) {}
