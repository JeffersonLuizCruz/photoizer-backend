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
        log.atInfo()
            .addKeyValue("event_action", "agenda.pagamento_registrado")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("valor", event.valor())
            .addKeyValue("extra_ecommerce", event.isExtraEcommerce())
            .log("Pagamento registrado no financeiro");

        var agendamento = agendamentoRepository.findByIdWithLock(event.agendamentoId())
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(event.agendamentoId()));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW) {
            log.atWarn()
                .addKeyValue("event_action", "agenda.pagamento_ignorado")
                .addKeyValue("agendamento_id", agendamento.getId())
                .addKeyValue("status", agendamento.getStatus())
                .log("Pagamento ignorado para agendamento cancelado/no-show");
            return;
        }

        if (event.isExtraEcommerce()) {
            agendamento.registrarPagamentoEcommerce(event.valor());
        } else {
            agendamento.registrarPagamento(event.valor());
        }
        agendamentoRepository.save(agendamento);

        log.atInfo()
            .addKeyValue("event_action", "agenda.valores_atualizados")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("valor_entrada_pago", agendamento.getValorEntradaPago())
            .addKeyValue("valor_restante", agendamento.getValorRestante())
            .addKeyValue("status", agendamento.getStatus())
            .log("Valores do agendamento atualizados");
    }

    @EventListener
    @Transactional
    public void handleExtrasAdicionados(ExtrasAdicionadosEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "agenda.extras_adicionados")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("tipo", event.tipo())
            .addKeyValue("valor_total", event.valorTotal())
            .log("Extras adicionados no financeiro");

        var agendamento = agendamentoRepository.findByIdWithLock(event.agendamentoId())
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(event.agendamentoId()));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW) {
            log.atWarn()
                .addKeyValue("event_action", "agenda.extras_ignorados")
                .addKeyValue("agendamento_id", agendamento.getId())
                .addKeyValue("status", agendamento.getStatus())
                .log("Extras ignorados para agendamento cancelado/no-show");
            return;
        }

        agendamento.adicionarExtras(event.valorTotal());
        agendamentoRepository.save(agendamento);

        log.atInfo()
            .addKeyValue("event_action", "agenda.extras_contabilizados")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("valor_extras", agendamento.getValorExtras())
            .addKeyValue("valor_total_final", agendamento.getValorTotalFinal())
            .log("Extras contabilizados no agendamento");
    }
}
