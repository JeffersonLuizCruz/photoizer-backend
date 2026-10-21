package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.model.TipoPagamento;
import com.photoizer.crm.financeiro.api.PagamentoRequest;
import com.photoizer.crm.financeiro.event.PagamentoRegistradoEvent;
import com.photoizer.crm.financeiro.exception.AgendamentoNaoEncontradoParaFinanceiroException;
import com.photoizer.crm.financeiro.exception.OperacaoNaoPermitidaException;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.financeiro.api.PagamentoResponse;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import com.photoizer.crm.shared.storage.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            AgendamentoRepository agendamentoRepository,
                            ApplicationEventPublisher eventPublisher,
                            FileStorageService fileStorageService,
                            FileValidator fileValidator) {
        this.pagamentoRepository = pagamentoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.eventPublisher = eventPublisher;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
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
        return criarPagamento(agendamentoId, request);
    }

    /**
     * Registra um recebimento com comprovante opcional (entrada/parcial/final).
     * Usado pela tela financeira do agendamento para baixar recebimentos de
     * agendamentos avulsos no modo percentual.
     */
    public Pagamento registrarRecebimento(UUID agendamentoId,
                                          BigDecimal valor,
                                          LocalDateTime dataPagamento,
                                          FormaPagamento formaPagamento,
                                          TipoPagamento tipoPagamento,
                                          MultipartFile comprovante,
                                          String observacao) {
        String urlComprovante = null;
        if (comprovante != null && !comprovante.isEmpty()) {
            fileValidator.validate(comprovante, "receipt");
            urlComprovante = fileStorageService.salvar(comprovante);
        }
        return criarPagamento(agendamentoId, new PagamentoRequest(
            valor, dataPagamento, urlComprovante, observacao, formaPagamento, tipoPagamento));
    }

    /**
     * Cria o pagamento sem a deduplicação por valor. Recebimentos manuais podem
     * ter o mesmo valor em datas diferentes (ex.: duas parcelas iguais).
     */
    private Pagamento criarPagamento(UUID agendamentoId, PagamentoRequest request) {
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
            .formaPagamento(request.formaPagamento())
            .tipoPagamento(request.tipoPagamento())
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

    /**
     * Registra pagamentos históricos/manuais sem publicar eventos. Usado pelo
     * Agendamento Avulso para preservar a data e a forma do recebimento original
     * sem acionar a máquina de estados do agenda.
     */
    public List<Pagamento> registrarPagamentosHistoricos(UUID agendamentoId, List<PagamentoManualCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoParaFinanceiroException(agendamentoId));

        var salvos = new ArrayList<Pagamento>();
        for (var command : commands) {
            if (command.valor() == null || command.valor().signum() <= 0) {
                continue;
            }
            var pagamento = Pagamento.builder()
                .agendamento(agendamento)
                .valor(command.valor())
                .dataPagamento(command.dataPagamento() != null ? command.dataPagamento() : LocalDateTime.now())
                .formaPagamento(command.formaPagamento())
                .tipoPagamento(command.tipoPagamento() != null ? command.tipoPagamento() : TipoPagamento.ENTRADA)
                .observacao(command.observacao())
                .build();
            salvos.add(pagamentoRepository.save(pagamento));
        }
        return salvos;
    }

    @Transactional(readOnly = true)
    public String caminhoComprovante(UUID pagamentoId) {
        return pagamentoRepository.findById(pagamentoId)
            .map(Pagamento::getUrlComprovante)
            .orElseThrow(() -> new com.photoizer.crm.financeiro.exception.PagamentoNaoEncontradoException(pagamentoId));
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponse> listarPagamentos(UUID agendamentoId) {
        return pagamentoRepository.findByAgendamentoId(agendamentoId).stream()
            .map(PagamentoResponse::of)
            .toList();
    }
}
