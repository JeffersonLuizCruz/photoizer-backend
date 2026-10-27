package com.photoizer.crm.agenda.acl;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.foto.acl.AgendamentoArmazenamentoReadService;
import com.photoizer.crm.foto.acl.AgendamentoResumo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter do módulo agenda para a porta de leitura do painel de armazenamento.
 * Mantém a regra de "ensaio finalizado" no módulo dono do status.
 */
@Service
public class AgendamentoArmazenamentoAdapter implements AgendamentoArmazenamentoReadService {

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoArmazenamentoAdapter(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public List<AgendamentoResumo> listar() {
        return agendamentoRepository.findAllComCliente().stream()
            .map(this::toResumo)
            .toList();
    }

    @Override
    public Optional<AgendamentoResumo> buscar(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId).map(this::toResumo);
    }

    @Override
    public boolean isFinalizado(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(agendamento -> agendamento.getStatus() == StatusAgendamento.FINALIZADO)
            .orElse(false);
    }

    private AgendamentoResumo toResumo(Agendamento agendamento) {
        var status = agendamento.getStatus();
        return new AgendamentoResumo(
            agendamento.getId(),
            agendamento.getCliente() != null ? agendamento.getCliente().getNome() : null,
            agendamento.getDataHoraEnsaio(),
            status != null ? status.name() : null,
            status == StatusAgendamento.FINALIZADO
        );
    }
}
