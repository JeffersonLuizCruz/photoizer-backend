package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;

public record AdminComprasRelatorioResponse(
    int totalCompras,
    int aguardandoComprovante,
    int aguardandoConfirmacao,
    int pagas,
    int canceladas,
    BigDecimal totalFaturado,
    BigDecimal totalAguardando
) {}
