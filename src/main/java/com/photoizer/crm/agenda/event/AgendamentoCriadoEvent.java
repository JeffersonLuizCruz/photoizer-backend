package com.photoizer.crm.agenda.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoCriadoEvent(
    UUID agendamentoId,
    UUID clienteId,
    UUID pacoteId,
    LocalDateTime dataHoraEnsaio,
    String indicadorNome,
    String indicadorTelefone,
    BigDecimal percentualComissao,
    BigDecimal valorBasePacote
) {
}
