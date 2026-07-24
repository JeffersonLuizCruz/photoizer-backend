package com.photoizer.crm.edicao.event;

import java.util.UUID;

public record RawEnviadosEvent(
    UUID agendamentoId,
    int totalFotos
) {
}
