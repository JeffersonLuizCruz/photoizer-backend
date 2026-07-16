package com.photoizer.crm.financeiro.listener;

import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FinanceiroEventListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroEventListener.class);

    @EventListener
    public void handleAgendamentoRealizado(AgendamentoRealizadoEvent event) {
        log.info("Agendamento {} realizado. Atualizando financeiro.", event.agendamentoId());
    }
}
