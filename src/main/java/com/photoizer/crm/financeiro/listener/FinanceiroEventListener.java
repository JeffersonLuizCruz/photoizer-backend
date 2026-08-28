package com.photoizer.crm.financeiro.listener;

import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.financeiro.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FinanceiroEventListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroEventListener.class);

    private final PagamentoService pagamentoService;

    public FinanceiroEventListener(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @EventListener
    public void handleCompraExtraConfirmada(CompraExtraConfirmadaEvent event) {
        log.info("Compra extras {} confirmada para agendamento {}. Contabilizando no financeiro.",
            event.compraExtraId(), event.agendamentoId());
        pagamentoService.registrarPagamentoExtraEcommerce(
            event.agendamentoId(), event.valorTotal(), event.compraExtraId());
    }
}
