package com.photoizer.crm.documento.listener;

import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.documento.service.ContratoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentoEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoEventListener.class);

    private final ContratoService contratoService;

    public DocumentoEventListener(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @EventListener
    public void handleAgendamentoConfirmado(AgendamentoConfirmadoEvent event) {
        log.info("Gerando contrato para agendamento confirmado {}", event.agendamentoId());
        contratoService.gerarContrato(event.agendamentoId());
    }
}
