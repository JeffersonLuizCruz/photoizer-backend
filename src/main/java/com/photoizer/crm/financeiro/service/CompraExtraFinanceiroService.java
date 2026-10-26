package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.event.ExtrasAdicionadosEvent;
import com.photoizer.crm.financeiro.event.ExtrasRemovidosEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Contabiliza no financeiro as compras extras originadas no e-commerce.
 *
 * <p>O checkout cria a compra e já a soma em {@code valorExtras/valorTotalFinal}.
 * A confirmação do pagamento soma o valor em {@code valorEntradaPago}. O
 * cancelamento estorna os extras. As flags em {@link CompraExtra} tornam o
 * processo idempotente e permitem reparar compras antigas no boot.
 */
@Service
@Transactional
public class CompraExtraFinanceiroService {

    private static final Logger log = LoggerFactory.getLogger(CompraExtraFinanceiroService.class);

    private final CompraExtraRepository compraExtraRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PagamentoService pagamentoService;
    private final ApplicationEventPublisher eventPublisher;

    public CompraExtraFinanceiroService(CompraExtraRepository compraExtraRepository,
                                        AgendamentoRepository agendamentoRepository,
                                        PagamentoService pagamentoService,
                                        ApplicationEventPublisher eventPublisher) {
        this.compraExtraRepository = compraExtraRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.pagamentoService = pagamentoService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Soma os extras da compra ao agendamento (total vendido e a receber).
     * Idempotente pela flag {@code extrasContabilizados}.
     */
    public void contabilizarCriacao(CompraExtra compra) {
        if (compra == null || compra.getStatus() == StatusCompraExtra.CANCELADA) {
            return;
        }
        if (Boolean.TRUE.equals(compra.getExtrasContabilizados())) {
            return;
        }
        if (agendamentoIndisponivel(compra.getAgendamentoId())) {
            marcarExtrasContabilizados(compra, true);
            return;
        }

        var quantidade = quantidade(compra);
        eventPublisher.publishEvent(new ExtrasAdicionadosEvent(
            compra.getAgendamentoId(),
            "FOTO",
            quantidade,
            valorUnitario(compra, quantidade),
            compra.getValorTotal()));

        marcarExtrasContabilizados(compra, true);
        log.atInfo()
            .addKeyValue("event_action", "financeiro.extras_ecommerce_contabilizados")
            .addKeyValue("agendamento_id", compra.getAgendamentoId())
            .addKeyValue("compra_extra_id", compra.getId())
            .addKeyValue("valor_total", compra.getValorTotal())
            .log("Extras da compra do e-commerce contabilizados no agendamento");
    }

    /**
     * Aplica o recebimento da compra ao agendamento.
     * Idempotente pela flag {@code pagamentoContabilizado}.
     */
    public void contabilizarPagamento(CompraExtra compra) {
        if (compra == null || compra.getStatus() != StatusCompraExtra.PAGA) {
            return;
        }
        if (agendamentoIndisponivel(compra.getAgendamentoId())) {
            marcarExtrasContabilizados(compra, true);
            marcarPagamentoContabilizado(compra, true);
            return;
        }

        contabilizarCriacao(compra);

        if (Boolean.TRUE.equals(compra.getPagamentoContabilizado())) {
            return;
        }

        pagamentoService.registrarPagamentoExtraEcommerce(
            compra.getAgendamentoId(),
            compra.getValorTotal(),
            compra.getId(),
            compra.getMetodoPagamento());
        marcarPagamentoContabilizado(compra, true);

        log.atInfo()
            .addKeyValue("event_action", "financeiro.recebimento_ecommerce_contabilizado")
            .addKeyValue("agendamento_id", compra.getAgendamentoId())
            .addKeyValue("compra_extra_id", compra.getId())
            .addKeyValue("valor_total", compra.getValorTotal())
            .log("Recebimento da compra extra contabilizado no agendamento");
    }

    /**
     * Estorna os extras de uma compra cancelada que já havia sido contabilizada.
     */
    public void contabilizarCancelamento(CompraExtra compra) {
        if (compra == null || !Boolean.TRUE.equals(compra.getExtrasContabilizados())) {
            return;
        }
        if (Boolean.TRUE.equals(compra.getPagamentoContabilizado())) {
            log.atWarn()
                .addKeyValue("event_action", "financeiro.estorno_extra_pago_ignorado")
                .addKeyValue("compra_extra_id", compra.getId())
                .addKeyValue("agendamento_id", compra.getAgendamentoId())
                .log("Compra cancelada possui pagamento contabilizado; estorno manual necessário");
            return;
        }

        eventPublisher.publishEvent(new ExtrasRemovidosEvent(
            compra.getAgendamentoId(), compra.getValorTotal()));
        marcarExtrasContabilizados(compra, false);

        log.atInfo()
            .addKeyValue("event_action", "financeiro.extras_ecommerce_estornados")
            .addKeyValue("agendamento_id", compra.getAgendamentoId())
            .addKeyValue("compra_extra_id", compra.getId())
            .addKeyValue("valor_total", compra.getValorTotal())
            .log("Extras da compra cancelada estornados do agendamento");
    }

    private boolean agendamentoIndisponivel(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(a -> a.getStatus() == StatusAgendamento.CANCELADO
                || a.getStatus() == StatusAgendamento.NO_SHOW)
            .orElse(true);
    }

    private void marcarExtrasContabilizados(CompraExtra compra, boolean valor) {
        compra.setExtrasContabilizados(valor);
        compraExtraRepository.save(compra);
    }

    private void marcarPagamentoContabilizado(CompraExtra compra, boolean valor) {
        compra.setPagamentoContabilizado(valor);
        compraExtraRepository.save(compra);
    }

    private int quantidade(CompraExtra compra) {
        return compra.getQuantidadeFotos() != null && compra.getQuantidadeFotos() > 0
            ? compra.getQuantidadeFotos()
            : 1;
    }

    private BigDecimal valorUnitario(CompraExtra compra, int quantidade) {
        return compra.getValorTotal()
            .divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }
}
