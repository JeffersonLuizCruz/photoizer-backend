package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

public record SelecaoCanceladaEvent(
    UUID agendamentoId,
    UUID selecaoId,
    List<UUID> fotoIds
) {}
