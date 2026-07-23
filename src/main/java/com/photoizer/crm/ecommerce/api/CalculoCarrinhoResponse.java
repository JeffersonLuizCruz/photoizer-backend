package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;
import java.util.List;

public record CalculoCarrinhoResponse(
    List<CalculoItemResponse> itens,
    int quantidade,
    BigDecimal valorUnitario,
    BigDecimal subtotal,
    BigDecimal total
) {}
