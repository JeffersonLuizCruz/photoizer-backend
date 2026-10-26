package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.ModoGaleria;
import jakarta.validation.constraints.NotNull;

public record AlterarModoGaleriaRequest(
    @NotNull(message = "Modo é obrigatório")
    ModoGaleria modo
) {}
