package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.EntregaPendenteEvent;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Job diário de lembrete de entregas pendentes.
 *
 * <p>Publica um {@link EntregaPendenteEvent} por agendamento em EM_EDICAO. O
 * módulo notificacao consome o evento e cria uma notificação por usuário staff,
 * deduplicada por dia. O agendamento não conhece os destinatários (Modulith).
 */
@Service
public class EntregaPendenteReminderService {

    private static final Logger log = LoggerFactory.getLogger(EntregaPendenteReminderService.class);

    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EntregaPendenteReminderService(AgendamentoRepository agendamentoRepository,
                                          ApplicationEventPublisher eventPublisher) {
        this.agendamentoRepository = agendamentoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "${app.entrega-pendente.lembrete.cron:0 0 9 * * *}", zone = "America/Sao_Paulo")
    public void lembrarEntregasPendentes() {
        var pendentes = agendamentoRepository.findByStatus(StatusAgendamento.EM_EDICAO);

        for (var agendamento : pendentes) {
            var clienteNome = agendamento.getCliente() != null
                ? agendamento.nomeCliente() : "Cliente não informado";
            var pacoteNome = agendamento.getPacote() != null
                ? agendamento.getPacote().getNome() : "Pacote";

            eventPublisher.publishEvent(new EntregaPendenteEvent(
                agendamento.getId(),
                clienteNome,
                pacoteNome,
                agendamento.getDataHoraEnsaio(),
                true
            ));
        }

        log.atInfo()
            .addKeyValue("event_action", "entrega_pendente.lembrete_diario")
            .addKeyValue("entregas_pendentes", pendentes.size())
            .log("Lembrete diário de entregas pendentes publicado");
    }
}
