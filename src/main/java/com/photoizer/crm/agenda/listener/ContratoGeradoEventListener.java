package com.photoizer.crm.agenda.listener;

import com.photoizer.crm.agenda.event.ContratoGeradoEvent;
import com.photoizer.crm.agenda.service.AgendamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consome o ContratoGeradoEvent publicado pelo módulo 'documento' após
 * gerar com sucesso o PDF de contrato.
 *
 * Marca contratoGerado = true no agendamento, mantendo a máquina de
 * estados do agendamento sob domínio do módulo 'agenda'.
 */
@Component
public class ContratoGeradoEventListener {

    private static final Logger log = LoggerFactory.getLogger(ContratoGeradoEventListener.class);

    private final AgendamentoService agendamentoService;

    public ContratoGeradoEventListener(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @EventListener
    public void handle(ContratoGeradoEvent event) {
        log.info("Marcando contratoGerado=true para agendamento {}", event.agendamentoId());
        agendamentoService.marcarContratoGerado(event.agendamentoId());
    }
}
