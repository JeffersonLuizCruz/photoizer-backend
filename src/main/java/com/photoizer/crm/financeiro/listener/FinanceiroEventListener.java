package com.photoizer.crm.financeiro.listener;

import com.photoizer.crm.ecommerce.event.CompraExtraCanceladaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraCriadaEvent;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.service.CompraExtraFinanceiroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FinanceiroEventListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroEventListener.class);

    private final CompraExtraRepository compraExtraRepository;
    private final CompraExtraFinanceiroService compraExtraFinanceiroService;

    public FinanceiroEventListener(CompraExtraRepository compraExtraRepository,
                                   CompraExtraFinanceiroService compraExtraFinanceiroService) {
        this.compraExtraRepository = compraExtraRepository;
        this.compraExtraFinanceiroService = compraExtraFinanceiroService;
    }

    @EventListener
    public void handleCompraExtraCriada(CompraExtraCriadaEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "financeiro.compra_extra_criada")
            .addKeyValue("compra_extra_id", event.compraExtraId())
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("valor_total", event.valorTotal())
            .log("Compra extra criada; contabilizando extras no agendamento");

        compraExtraRepository.findById(event.compraExtraId())
            .ifPresentOrElse(
                compraExtraFinanceiroService::contabilizarCriacao,
                () -> log.atWarn()
                    .addKeyValue("event_action", "financeiro.compra_extra_nao_encontrada")
                    .addKeyValue("compra_extra_id", event.compraExtraId())
                    .log("Compra extra não encontrada para contabilização"));
    }

    @EventListener
    public void handleCompraExtraConfirmada(CompraExtraConfirmadaEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "financeiro.compra_extra_confirmada")
            .addKeyValue("compra_extra_id", event.compraExtraId())
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("valor_total", event.valorTotal())
            .log("Compra extra confirmada; contabilizando recebimento no agendamento");

        compraExtraRepository.findById(event.compraExtraId())
            .ifPresentOrElse(
                compraExtraFinanceiroService::contabilizarPagamento,
                () -> log.atWarn()
                    .addKeyValue("event_action", "financeiro.compra_extra_nao_encontrada")
                    .addKeyValue("compra_extra_id", event.compraExtraId())
                    .log("Compra extra não encontrada para confirmação"));
    }

    @EventListener
    public void handleCompraExtraCancelada(CompraExtraCanceladaEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "financeiro.compra_extra_cancelada")
            .addKeyValue("compra_extra_id", event.compraExtraId())
            .addKeyValue("agendamento_id", event.agendamentoId())
            .log("Compra extra cancelada; avaliando estorno no agendamento");

        compraExtraRepository.findById(event.compraExtraId())
            .ifPresentOrElse(
                compraExtraFinanceiroService::contabilizarCancelamento,
                () -> log.atWarn()
                    .addKeyValue("event_action", "financeiro.compra_extra_nao_encontrada")
                    .addKeyValue("compra_extra_id", event.compraExtraId())
                    .log("Compra extra não encontrada para cancelamento"));
    }
}
