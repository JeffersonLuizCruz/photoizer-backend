package com.photoizer.crm.notificacao.listener;

import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.agenda.model.StatusTarefa;
import com.photoizer.crm.agenda.repository.TarefaRepository;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AlertaPrazoListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaPrazoListener.class);

    private final NotificacaoService notificacaoService;
    private final TarefaRepository tarefaRepository;

    public AlertaPrazoListener(NotificacaoService notificacaoService, TarefaRepository tarefaRepository) {
        this.notificacaoService = notificacaoService;
        this.tarefaRepository = tarefaRepository;
    }

    @EventListener
    public void onAgendamentoConfirmado(AgendamentoConfirmadoEvent event) {
        var tarefas = tarefaRepository.findByAgendamentoIdOrderByDataLimiteAsc(event.agendamentoId());
        var agora = LocalDateTime.now();

        for (var tarefa : tarefas) {
            if (tarefa.getStatus() == StatusTarefa.PENDENTE
                && tarefa.getDataLimite() != null
                && tarefa.getDataLimite().isBefore(agora.plusDays(1))) {
                log.info("Alerta: tarefa '{}' do agendamento {} está próxima do prazo ({})",
                    tarefa.getTipo(), event.agendamentoId(), tarefa.getDataLimite());
                notificacaoService.enviarAlerta(
                    "admin",
                    "Prazo próximo: tarefa " + tarefa.getTipo()
                );
            }
        }
    }
}
