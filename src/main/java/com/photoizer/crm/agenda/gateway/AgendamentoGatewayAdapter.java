package com.photoizer.crm.agenda.gateway;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.despesa.service.DespesaAgendamentoGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter para a porta DespesaAgendamentoGateway.
 *
 * Implementa a interface definida no módulo despesa (porta),
 * delegando para AgendamentoRepository (adapter concreto).
 * Fluxo de dependência: despesa → porta ← agenda (este componente).
 */
@Component
public class AgendamentoGatewayAdapter implements DespesaAgendamentoGateway {

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoGatewayAdapter(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public boolean existsById(UUID agendamentoId) {
        return agendamentoRepository.existsById(agendamentoId);
    }
}
