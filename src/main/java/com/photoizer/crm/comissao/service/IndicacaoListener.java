package com.photoizer.crm.comissao.service;

import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent;
import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.indicador.service.IndicadorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
public class IndicacaoListener {

    private static final Logger log = LoggerFactory.getLogger(IndicacaoListener.class);

    private final IndicacaoService indicacaoService;
    private final IndicadorService indicadorService;

    public IndicacaoListener(IndicacaoService indicacaoService,
                             IndicadorService indicadorService) {
        this.indicacaoService = indicacaoService;
        this.indicadorService = indicadorService;
    }

    @EventListener
    @Transactional
    public void handleAgendamentoCriado(AgendamentoCriadoEvent event) {
        if (event.indicadorNome() == null || event.indicadorNome().isBlank()) return;
        if (event.indicadorTelefone() == null || event.indicadorTelefone().isBlank()) return;

        var percentual = event.percentualComissao() != null ? event.percentualComissao() : BigDecimal.TEN;
        log.info("Criando indicação (PACOTE) para agendamento {}: indicador={}", event.agendamentoId(), event.indicadorNome());

        var indicador = indicadorService.buscarOuCriar(event.indicadorNome(), event.indicadorTelefone());

        indicacaoService.criar(
            event.agendamentoId(),
            indicador.getId(),
            event.indicadorNome(),
            event.indicadorTelefone(),
            "PACOTE",
            percentual,
            event.valorBasePacote()
        );
    }

    @EventListener
    @Transactional
    public void handlePagamentoRegistrado(PagamentoFinalRegistradoEvent event) {
        log.info("Marcando todas as comissões como pagas para agendamento {}", event.agendamentoId());
        indicacaoService.marcarTodasComoPaga(event.agendamentoId());
    }

    @EventListener
    @Transactional
    public void handleAgendamentoCancelado(AgendamentoCanceladoEvent event) {
        log.info("Cancelando todas as comissões para agendamento {}", event.agendamentoId());
        indicacaoService.marcarTodasComoCancelada(event.agendamentoId());
    }
}
