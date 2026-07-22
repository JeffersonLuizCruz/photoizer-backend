package com.photoizer.crm.agenda.event;

import java.util.UUID;

public record AgendamentoCanceladoEvent(
    UUID agendamentoId
) {
}
