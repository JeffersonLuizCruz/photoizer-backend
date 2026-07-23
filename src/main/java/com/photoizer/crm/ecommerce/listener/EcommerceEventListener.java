package com.photoizer.crm.ecommerce.listener;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraCriadaEvent;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EcommerceEventListener {

    private static final Logger log = LoggerFactory.getLogger(EcommerceEventListener.class);

    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoService notificacaoService;

    public EcommerceEventListener(AgendamentoRepository agendamentoRepository,
                                   NotificacaoService notificacaoService) {
        this.agendamentoRepository = agendamentoRepository;
        this.notificacaoService = notificacaoService;
    }

    @EventListener
    @Transactional
    public void handleCompraExtraCriada(CompraExtraCriadaEvent event) {
        log.info("Nova compra extra criada: agendamento={}, compra={}, valor={}, fotos={}",
            event.agendamentoId(), event.compraExtraId(), event.valorTotal(), event.quantidadeFotos());

        notificacaoService.notificarNovaCompraExtra(
            event.agendamentoId(), event.compraExtraId(), event.valorTotal(), event.quantidadeFotos());
    }

    @EventListener
    @Transactional
    public void handleCompraExtraConfirmada(CompraExtraConfirmadaEvent event) {
        log.info("Compra extra confirmada: agendamento={}, compra={}, valor={}",
            event.agendamentoId(), event.compraExtraId(), event.valorTotal());

        var agendamento = agendamentoRepository.findById(event.agendamentoId())
            .orElse(null);
        if (agendamento == null) {
            log.warn("Agendamento não encontrado: {}", event.agendamentoId());
            return;
        }

        var extrasAtual = agendamento.getValorExtras() != null ? agendamento.getValorExtras() : java.math.BigDecimal.ZERO;
        agendamento.setValorExtras(extrasAtual.add(event.valorTotal()));

        var totalFinalAtual = agendamento.getValorTotalFinal() != null ? agendamento.getValorTotalFinal() : java.math.BigDecimal.ZERO;
        agendamento.setValorTotalFinal(totalFinalAtual.add(event.valorTotal()));

        agendamentoRepository.save(agendamento);

        notificacaoService.notificarCompraExtraConfirmada(
            event.agendamentoId(), event.compraExtraId(), event.valorTotal());
    }
}
