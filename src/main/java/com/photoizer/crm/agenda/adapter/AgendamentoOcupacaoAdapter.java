package com.photoizer.crm.agenda.adapter;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.service.DisponibilidadeService;
import com.photoizer.crm.shared.port.AgendamentoOcupacaoPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementacao da porta AgendamentoOcupacaoPort para o modulo bloqueio.
 * Mantem a regra de quais status ocupam a agenda centralizada no modulo agenda.
 */
@Component
public class AgendamentoOcupacaoAdapter implements AgendamentoOcupacaoPort {

    private static final int BUFFER_HORAS = 24;

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoOcupacaoAdapter(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public List<ConflitoAgendamento> buscarConflitos(LocalDateTime inicio, LocalDateTime fim, UUID fotografoId) {
        var inicioMenosBuffer = inicio.minusHours(BUFFER_HORAS);
        var statusesIgnorados = DisponibilidadeService.STATUS_IGNORADOS;

        var candidatos = fotografoId != null
            ? agendamentoRepository.findAtivosParaConflitoNoPeriodoByFotografo(
                fotografoId, inicioMenosBuffer, fim, statusesIgnorados)
            : agendamentoRepository.findAtivosParaConflitoNoPeriodo(
                inicioMenosBuffer, fim, statusesIgnorados);

        return candidatos.stream()
            .filter(a -> sobrepoe(a, inicio, fim))
            .map(this::toConflito)
            .toList();
    }

    private boolean sobrepoe(Agendamento agendamento, LocalDateTime inicio, LocalDateTime fim) {
        var inicioAgendamento = agendamento.getDataHoraEnsaio();
        var duracao = agendamento.getDuracaoMinutos() != null ? agendamento.getDuracaoMinutos() : 0;
        var fimAgendamento = inicioAgendamento.plusMinutes(duracao);
        return inicioAgendamento.isBefore(fim) && fimAgendamento.isAfter(inicio);
    }

    private ConflitoAgendamento toConflito(Agendamento agendamento) {
        return new ConflitoAgendamento(
            agendamento.getId(),
            agendamento.getDataHoraEnsaio(),
            agendamento.getDuracaoMinutos() != null ? agendamento.getDuracaoMinutos() : 0,
            agendamento.getLocalEnsaio()
        );
    }
}
