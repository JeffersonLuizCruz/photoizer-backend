package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.CompraExtraCanceladaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraPagaEvent;
import com.photoizer.crm.ecommerce.exception.CompraJaPagaException;
import com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
