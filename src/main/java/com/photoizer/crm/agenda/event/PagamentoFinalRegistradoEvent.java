package com.photoizer.crm.agenda.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoFinalRegistradoEvent(
    UUID agendamentoId,
    BigDecimal valorPago
) {
}
