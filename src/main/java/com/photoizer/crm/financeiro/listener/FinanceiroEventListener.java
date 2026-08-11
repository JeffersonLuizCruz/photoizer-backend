package com.photoizer.crm.financeiro.listener;

import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.financeiro.service.FinanceiroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FinanceiroEventListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroEventListener.class);

    private final FinanceiroService financeiroService;

    public FinanceiroEventListener(FinanceiroService financeiroService) {
        this.financeiroService = financeiroService;
    }

    @EventListener
    public void handleAgendamentoRealizado(AgendamentoRealizadoEvent event) {
        log.info("Agendamento {} realizado. Atualizando financeiro.", event.agendamentoId());
    }

    @EventListener
    public void handleCompraExtraConfirmada(CompraExtraConfirmadaEvent event) {
        log.info("Compra extras {} confirmada para agendamento {}. Contabilizando no financeiro.",
            event.compraExtraId(), event.agendamentoId());
        financeiroService.registrarPagamentoExtraEcommerce(
            event.agendamentoId(), event.valorTotal(), event.compraExtraId());
    }
}
