package com.photoizer.crm.agenda.repository.projection;

import com.photoizer.crm.agenda.model.RepasseStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeção leve para agregação de repasses por agendamento.
 * Substitui Object[] de sumRepassesAtivosPorAgendamento por type-safe interface.
 */
public interface RepasseAggregation {
    UUID getAgendamentoId();
    RepasseStatus getStatus();
    BigDecimal getValor();
}
