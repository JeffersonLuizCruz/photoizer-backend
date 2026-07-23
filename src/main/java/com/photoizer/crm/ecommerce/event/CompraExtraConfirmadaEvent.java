package com.photoizer.crm.ecommerce.event;

import java.math.BigDecimal;
import java.util.UUID;

public record CompraExtraConfirmadaEvent(
    UUID agendamentoId,
    UUID compraExtraId,
    BigDecimal valorTotal
) {}
