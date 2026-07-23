package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CupomValidacaoRequest(
    @NotBlank String codigo,
    BigDecimal valorPedido
) {}
