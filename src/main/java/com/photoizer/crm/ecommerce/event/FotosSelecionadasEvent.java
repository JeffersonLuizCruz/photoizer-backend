package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

public record FotosSelecionadasEvent(
    UUID agendamentoId,
    List<UUID> fotoIds,
    boolean selecionada
) {}
