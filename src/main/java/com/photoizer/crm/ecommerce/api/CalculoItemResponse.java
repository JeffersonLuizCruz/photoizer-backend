package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CalculoItemResponse(
    UUID fotoId,
    String fileName,
    BigDecimal valorUnitario
) {}
