package com.photoizer.crm.notificacao.listener;

import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LembreteEnsaioListener {

    private static final Logger log = LoggerFactory.getLogger(LembreteEnsaioListener.class);

    private final NotificacaoService notificacaoService;

    public LembreteEnsaioListener(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @EventListener
    public void handleAgendamentoConfirmado(AgendamentoConfirmadoEvent event) {
        log.info("Programando lembrete para ensaio do agendamento {}", event.agendamentoId());
        notificacaoService.enviarLembrete(
            "cliente_" + event.clienteId(),
            "Seu ensaio foi confirmado! Em breve enviaremos mais detalhes."
        );
    }
}
