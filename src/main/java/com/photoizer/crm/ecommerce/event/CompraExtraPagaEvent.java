package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

public record CompraExtraPagaEvent(
    UUID compraExtraId,
    UUID agendamentoId
) {}
