package com.photoizer.crm.shared.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.shared.port.DisplacementCostPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * PATTERN: Adapter (Hexagonal Architecture / Ports & Adapters)
 *
 * Implementação de DisplacementCostPort com regra de negócio de deslocamento.
 * Permanece em shared/service/ pois é lógica pura baseada em campos da entidade.
 *
 * Regra: repassarDeslocamento=true → custo absorvido pelo cliente (ZERO).
 * Senão → retorna custoDeslocamento (ou ZERO se nulo).
 */
@Component
public class DisplacementCostAdapter implements DisplacementCostPort {

    @Override
    public BigDecimal deslocamentoEfetivo(Agendamento agendamento) {
        if (Boolean.TRUE.equals(agendamento.getRepassarDeslocamento())) {
            return BigDecimal.ZERO;
        }
        return agendamento.getCustoDeslocamento() != null
            ? agendamento.getCustoDeslocamento()
            : BigDecimal.ZERO;
    }
}
