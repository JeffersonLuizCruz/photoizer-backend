package com.photoizer.crm.agenda.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoCriadoEvent(
    UUID agendamentoId,
    UUID clienteId,
    UUID pacoteId,
    LocalDateTime dataHoraEnsaio
) {
}
