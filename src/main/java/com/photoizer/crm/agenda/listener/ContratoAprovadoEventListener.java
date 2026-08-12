package com.photoizer.crm.agenda.listener;

import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.contrato.event.ContratoAprovadoEvent;
import com.photoizer.crm.contrato.repository.ContratoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ContratoAprovadoEventListener {

    private static final Logger log = LoggerFactory.getLogger(ContratoAprovadoEventListener.class);

    private final AgendamentoService agendamentoService;
    private final ContratoRepository contratoRepository;

    public ContratoAprovadoEventListener(AgendamentoService agendamentoService,
                                         ContratoRepository contratoRepository) {
        this.agendamentoService = agendamentoService;
        this.contratoRepository = contratoRepository;
    }

    @EventListener
    public void handle(ContratoAprovadoEvent event) {
        log.info("Materializando agendamento a partir do contrato aprovado {}", event.contratoId());
        var agendamento = agendamentoService.criarAgendamentoDeContrato(event);
        contratoRepository.findById(event.contratoId())
            .ifPresent(contrato -> {
                contrato.setAgendamentoId(agendamento.getId());
                contratoRepository.save(contrato);
            });
    }
}