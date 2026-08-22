package com.photoizer.crm.agenda.listener;

import com.photoizer.crm.ecommerce.event.TokenGaleriaRegeneradoEvent;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PATTERN: Event Listener (Modulith)
 * Escuta eventos do módulo ecommerce e atualiza entidades Agendamento.
 * Motivo: desacoplar o módulo ecommerce do módulo agenda - o ecommerce
 * não deve escrever diretamente em Agendamento, apenas publicar eventos.
 */
@Component
public class AgendamentoEcommerceEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoEcommerceEventListener.class);

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoEcommerceEventListener(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @EventListener
    @Transactional
    public void handleTokenGaleriaRegenerado(TokenGaleriaRegeneradoEvent event) {
        log.info("Recebido TokenGaleriaRegeneradoEvent: agendamento={}, novoToken={}",
            event.agendamentoId(), event.novoToken());
        var agendamento = agendamentoRepository.findById(event.agendamentoId());
        agendamento.ifPresent(agg -> {
            agg.setTokenGaleria(event.novoToken());
            agg.setTokenExpiracao(event.expiracao());
            agendamentoRepository.save(agg);
        });
    }
}
