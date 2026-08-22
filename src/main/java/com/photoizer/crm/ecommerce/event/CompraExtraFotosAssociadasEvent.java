package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

public record CompraExtraFotosAssociadasEvent(
    UUID compraExtraId,
    UUID agendamentoId,
    List<UUID> fotoIds
) {}
