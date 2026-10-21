package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.CompraExtraCanceladaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraPagaEvent;
import com.photoizer.crm.ecommerce.exception.CompraJaPagaException;
import com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.exception.ConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PATTERN: State Pattern + Facade Pattern
 * Centraliza todas as operações de transição de estado de CompraExtra.
 * Utiliza os métodos definidos no enum StatusCompraExtra para validar
 * transições, eliminando os if/else espalhados no antigo EcommerceService.
 *
 * MODULITH: Escritas em FotoEnsaio são feitas via eventos.
 * O módulo ecommerce NÃO deve escrever diretamente em FotoEnsaio.
 */
@Service
@Transactional
public class PagamentoExtraService {

    private final CompraExtraRepository compraExtraRepository;
    private final GaleriaQueryService galeriaQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public PagamentoExtraService(CompraExtraRepository compraExtraRepository,
                                 GaleriaQueryService galeriaQueryService,
                                 ApplicationEventPublisher eventPublisher) {
        this.compraExtraRepository = compraExtraRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.eventPublisher = eventPublisher;
    }

    public void confirmarPagamento(UUID compraExtraId) {
        var compra = compraExtraRepository.findById(compraExtraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraExtraId));
        marcarCompraPaga(compra);
    }

    /**
     * Simulação de pagamento usada apenas em desenvolvimento/testes manuais.
     * NÃO deve ser exposta por endpoint público: ver {@code DevPagamentoController}
     * (ativo somente no perfil {@code dev} e restrito a ADMIN).
     */
    public CompraExtra simularPagamento(UUID agendamentoId, UUID compraExtraId) {
        var compra = compraExtraRepository.findById(compraExtraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraExtraId));

        if (!compra.getAgendamentoId().equals(agendamentoId)) {
            throw new CompraNaoEncontradaException(compraExtraId);
        }

        if (compra.getStatus() != StatusCompraExtra.PAGA) {
            marcarCompraPaga(compra);
        }
        return compra;
    }

    /**
     * Define ou remove o link de pagamento por cartao de credito da compra.
     * O link e gerado manualmente pelo staff e apenas exibido ao cliente.
     */
    public CompraExtra atualizarLinkPagamentoCartao(UUID compraId, String linkPagamentoCartao) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));

        if (compra.getStatus() == StatusCompraExtra.PAGA || compra.getStatus() == StatusCompraExtra.CANCELADA) {
            throw new ConflictException("Compra paga ou cancelada nao pode receber link de pagamento");
        }

        compra.setLinkPagamentoCartao(validarLink(linkPagamentoCartao));
        return compraExtraRepository.save(compra);
    }

    private String validarLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        try {
            var uri = URI.create(link.trim());
            var scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new BadRequestException("Link de pagamento deve ser uma URL http ou https valida");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Link de pagamento deve ser uma URL http ou https valida");
        }
    }

    public void cancelarCompra(UUID compraId, String motivoRecusa) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));

        if (!compra.getStatus().podeSerCancelada()) {
            throw new CompraJaPagaException();
        }

        compra.setStatus(StatusCompraExtra.CANCELADA);
        if (motivoRecusa != null && !motivoRecusa.isBlank()) {
            compra.setMotivoRecusa(motivoRecusa.trim());
        }
        compraExtraRepository.save(compra);

        eventPublisher.publishEvent(new CompraExtraCanceladaEvent(
            compra.getId(), compra.getAgendamentoId()));
    }

    private void marcarCompraPaga(CompraExtra compra) {
        compra.setStatus(StatusCompraExtra.PAGA);
        compra.setDataPagamento(LocalDateTime.now());
        compraExtraRepository.save(compra);

        eventPublisher.publishEvent(new CompraExtraPagaEvent(
            compra.getId(), compra.getAgendamentoId()));

        eventPublisher.publishEvent(new CompraExtraConfirmadaEvent(
            compra.getAgendamentoId(), compra.getId(), compra.getValorTotal()));
    }
}
