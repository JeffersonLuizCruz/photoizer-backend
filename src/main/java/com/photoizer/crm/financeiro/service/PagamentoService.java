package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.financeiro.api.PagamentoRequest;
import com.photoizer.crm.financeiro.event.PagamentoRegistradoEvent;
import com.photoizer.crm.financeiro.exception.AgendamentoNaoEncontradoParaFinanceiroException;
import com.photoizer.crm.financeiro.exception.OperacaoNaoPermitidaException;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.financeiro.api.PagamentoResponse;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service responsável pelo registro e listagem de pagamentos.
 *
 * Pattern: SRP — extraído de FinanceiroService (que misturava pagamentos, extras, queries e relatórios).
 * Não muta mais diretamente o Agendamento — publica PagamentoRegistradoEvent para que o agenda
 * atualize seu próprio estado via listener.
 */
@Service
@Transactional
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    private final PagamentoRepository pagamentoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            AgendamentoRepository agendamentoRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.pagamentoRepository = pagamentoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registra um pagamento para um agendamento.
     * Publica PagamentoRegistradoEvent para que o módulo agenda atualize status e valores.
     */
    public Pagamento registrarPagamento(UUID agendamentoId, PagamentoRequest request) {
        var existente = pagamentoRepository.findByAgendamentoIdAndValorAndCompraExtraIdIsNull(
            agendamentoId, request.valor());
        if (existente.isPresent()) {
            log.warn("Pagamento de R$ {} para agendamento {} já registrado. Ignorando duplicata.",
                request.valor(), agendamentoId);
            return existente.get();
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoParaFinanceiroException(agendamentoId));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW) {
            throw new OperacaoNaoPermitidaException(
                "Não é possível registrar pagamento para agendamento " + agendamento.getStatus());
        }

        var pagamento = Pagamento.builder()
            .agendamento(agendamento)
            .valor(request.valor())
            .dataPagamento(request.dataPagamento() != null ? request.dataPagamento() : LocalDateTime.now())
            .urlComprovante(request.urlComprovante())
            .observacao(request.observacao())
            .build();
        var saved = pagamentoRepository.save(pagamento);

        eventPublisher.publishEvent(new PagamentoRegistradoEvent(
            agendamentoId, saved.getId(), saved.getValor(), false, null));

        return saved;
    }

    /**
     * Registra pagamento de compra extra do e-commerce.
     * Publica PagamentoRegistradoEvent com isExtraEcommerce=true.
     */
    public Pagamento registrarPagamentoExtraEcommerce(UUID agendamentoId, BigDecimal valor, UUID compraExtraId) {
        var existente = pagamentoRepository.findByCompraExtraId(compraExtraId);
        if (existente.isPresent()) {
            log.warn("Pagamento para compra extra {} já registrado. Ignorando duplicata.", compraExtraId);
            return existente.get();
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoParaFinanceiroException(agendamentoId));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW) {
            throw new OperacaoNaoPermitidaException(
                "Não é possível registrar pagamento de e-commerce para agendamento " + agendamento.getStatus());
        }

        var pagamento = Pagamento.builder()
            .agendamento(agendamento)
            .valor(valor)
            .dataPagamento(LocalDateTime.now())
            .compraExtraId(compraExtraId)
            .observacao("Fotos extras (e-commerce)")
            .build();
        var saved = pagamentoRepository.save(pagamento);

        eventPublisher.publishEvent(new PagamentoRegistradoEvent(
            agendamentoId, saved.getId(), valor, true, compraExtraId));

        return saved;
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponse> listarPagamentos(UUID agendamentoId) {
        return pagamentoRepository.findByAgendamentoId(agendamentoId).stream()
            .map(PagamentoResponse::of)
            .toList();
    }
}
