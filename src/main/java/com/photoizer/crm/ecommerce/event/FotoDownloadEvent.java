package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

public record FotoDownloadEvent(
    UUID agendamentoId,
    List<UUID> fotoIds
) {}
