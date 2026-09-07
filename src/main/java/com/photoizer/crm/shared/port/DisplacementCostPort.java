package com.photoizer.crm.shared.port;

import com.photoizer.crm.agenda.model.Agendamento;

import java.math.BigDecimal;

/**
 * PATTERN: Port (Hexagonal Architecture / Ports & Adapters)
 *
 * Porta de cálculo de custo de deslocamento efetivo.
 * Lógica pura de negócio baseada em campos do Agendamento.
 *
 * Regra: Se repassarDeslocamento=true, custo é absorvido pelo cliente (ZERO).
 * Caso contrário, retorna o custoDeslocamento do agendamento.
 *
 * Adaptador: DisplacementCostAdapter (permanece em shared/service/,
 * pois é lógica pura sem dependência de repositório).
 */
public interface DisplacementCostPort {

    BigDecimal deslocamentoEfetivo(Agendamento agendamento);
}
