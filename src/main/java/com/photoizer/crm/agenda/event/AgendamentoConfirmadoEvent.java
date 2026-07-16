package com.photoizer.crm.agenda.event;

import java.util.UUID;

public record AgendamentoConfirmadoEvent(
    UUID agendamentoId,
    UUID clienteId
) {
}
