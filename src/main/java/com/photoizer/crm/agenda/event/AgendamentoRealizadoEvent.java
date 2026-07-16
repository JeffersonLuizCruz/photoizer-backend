package com.photoizer.crm.agenda.event;

import java.util.UUID;

public record AgendamentoRealizadoEvent(
    UUID agendamentoId,
    UUID clienteId
) {
}
