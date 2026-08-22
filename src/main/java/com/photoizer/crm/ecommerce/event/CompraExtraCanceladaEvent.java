package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

public record CompraExtraCanceladaEvent(
    UUID compraExtraId,
    UUID agendamentoId
) {}
