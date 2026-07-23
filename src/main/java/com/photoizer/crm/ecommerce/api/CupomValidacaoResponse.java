package com.photoizer.crm.ecommerce.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CupomValidacaoResponse(
    boolean valido,
    String mensagem,
    UUID cupomId,
    String codigo,
    String tipoDesconto,
    BigDecimal valorDesconto,
    BigDecimal valorComDesconto
) {}
