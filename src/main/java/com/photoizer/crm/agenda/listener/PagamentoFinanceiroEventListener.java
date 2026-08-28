package com.photoizer.crm.agenda.listener;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.financeiro.event.ExtrasAdicionadosEvent;
import com.photoizer.crm.financeiro.event.PagamentoRegistradoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener que consome eventos financeiros para atualizar o estado do Agendamento.
 *
 * Pattern: Event Listener — o módulo agenda é dono da máquina de estados do Agendamento.
 * Quando o financeiro registra pagamento ou adiciona extras, este listener atualiza
 * os valores e status do agendamento de forma consistente.
 *
 * Isso elimina a escrita cross-module direta que existia no FinanceiroService.
 */
@Component
public class PagamentoFinanceiroEventListener {

    private static final Logger log = LoggerFactory.getLogger(PagamentoFinanceiroEventListener.class);

    private final AgendamentoRepository agendamentoRepository;

    public PagamentoFinanceiroEventListener(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @EventListener
    @Transactional
    public void handlePagamentoRegistrado(PagamentoRegistradoEvent event) {
        log.info("Pagamento registrado no financeiro: agendamento={}, valor={}, extraEcommerce={}",
            event.agendamentoId(), event.valor(), event.isExtraEcommerce());

        var agendamento = agendamentoRepository.findByIdWithLock(event.agendamentoId())
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(event.agendamentoId()));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW) {
            log.warn("Pagamento ignorado: agendamento {} esta {}", agendamento.getId(), agendamento.getStatus());
            return;
        }

        if (event.isExtraEcommerce()) {
            agendamento.registrarPagamentoEcommerce(event.valor());
        } else {
            agendamento.registrarPagamento(event.valor());
        }
        agendamentoRepository.save(agendamento);

        log.info("Agendamento {} atualizado: valorEntradaPago={}, valorRestante={}, status={}",
            agendamento.getId(), agendamento.getValorEntradaPago(),
            agendamento.getValorRestante(), agendamento.getStatus());
    }

    @EventListener
    @Transactional
    public void handleExtrasAdicionados(ExtrasAdicionadosEvent event) {
        log.info("Extras adicionados no financeiro: agendamento={}, tipo={}, valorTotal={}",
            event.agendamentoId(), event.tipo(), event.valorTotal());

        var agendamento = agendamentoRepository.findByIdWithLock(event.agendamentoId())
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(event.agendamentoId()));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW) {
            log.warn("Ignorando extras para agendamento {} com status {}", agendamento.getId(), agendamento.getStatus());
            return;
        }

        agendamento.adicionarExtras(event.valorTotal());
        agendamentoRepository.save(agendamento);

        log.info("Agendamento {} atualizado: valorExtras={}, valorTotalFinal={}",
            agendamento.getId(), agendamento.getValorExtras(), agendamento.getValorTotalFinal());
    }
}
