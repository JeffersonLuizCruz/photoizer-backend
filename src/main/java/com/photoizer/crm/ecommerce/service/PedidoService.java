package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.PedidoRequest;
import com.photoizer.crm.ecommerce.model.Cupom;
import com.photoizer.crm.ecommerce.model.Pedido;
import com.photoizer.crm.ecommerce.repository.CupomRepository;
import com.photoizer.crm.ecommerce.repository.PedidoRepository;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de pedidos do e-commerce (RF009, RF010).
 * Calcula automaticamente: subtotal do pacote, subtotal das fotos extras
 * (quantidade x preço unitário), taxa de entrega, desconto de cupom e total geral.
 */
@Service
@Transactional
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PacoteRepository pacoteRepository;
    private final CupomRepository cupomRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         PacoteRepository pacoteRepository,
                         CupomRepository cupomRepository) {
        this.pedidoRepository = pedidoRepository;
        this.pacoteRepository = pacoteRepository;
        this.cupomRepository = cupomRepository;
    }

    public Pedido criar(UUID clienteId, PedidoRequest request) {
        var pacote = pacoteRepository.findById(request.pacoteId())
            .orElseThrow(() -> new IllegalArgumentException("Pacote não encontrado"));

        if (!pacote.getAtivo()) {
            throw new IllegalStateException("Pacote não está mais disponível");
        }

        // RF004: fotos até o limite entram no pacote; excedentes são extras
        var selecionadas = request.fotosSelecionadasIds() != null ? request.fotosSelecionadasIds() : List.<UUID>of();
        var extras = request.fotosExtrasIds() != null ? request.fotosExtrasIds() : List.<UUID>of();

        if (selecionadas.size() > pacote.getQuantidadeFotos()) {
            throw new IllegalArgumentException(
                "Seleção excede o limite de " + pacote.getQuantidadeFotos() + " fotos do pacote. "
                + "Fotos excedentes devem ser enviadas como extras.");
        }

        // RF009: cálculo automático
        var subtotalPacote = pacote.getValorBase();
        var precoFotoExtra = pacote.getPrecoFotoExtra() != null ? pacote.getPrecoFotoExtra() : new BigDecimal("15.00");
        var subtotalExtras = precoFotoExtra
            .multiply(BigDecimal.valueOf(extras.size()))
            .setScale(2, RoundingMode.HALF_UP);
        var taxaEntrega = request.taxaEntrega() != null ? request.taxaEntrega() : BigDecimal.ZERO;

        var subtotal = subtotalPacote.add(subtotalExtras).add(taxaEntrega);

        // FA001: aplicação de cupom com incremento de uso
        var desconto = BigDecimal.ZERO;
        if (request.codigoCupom() != null && !request.codigoCupom().isBlank()) {
            desconto = aplicarCupom(request.codigoCupom(), subtotal);
        }

        var total = subtotal.subtract(desconto).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        var pedido = Pedido.builder()
            .clienteId(clienteId)
            .pacoteId(request.pacoteId())
            .agendamentoId(request.agendamentoId())
            .fotosSelecionadasIds(toCsv(selecionadas))
            .fotosExtrasIds(toCsv(extras))
            .subtotalPacote(subtotalPacote)
            .subtotalExtras(subtotalExtras)
            .taxaEntrega(taxaEntrega)
            .desconto(desconto)
            .total(total)
            .status("AGUARDANDO_PAGAMENTO")
            .formaPagamento(request.formaPagamento())
            .opcaoEntrega(request.opcaoEntrega() != null ? request.opcaoEntrega() : "DIGITAL")
            .tokenGaleria(UUID.randomUUID())
            .dataPedido(LocalDateTime.now())
            .build();

        return pedidoRepository.save(pedido);
    }

    private BigDecimal aplicarCupom(String codigo, BigDecimal valorPedido) {
        var cupom = cupomRepository.findByCodigoIgnoreCase(codigo)
            .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado"));

        validarCupom(cupom, valorPedido);

        BigDecimal desconto;
        if ("PERCENTUAL".equals(cupom.getTipoDesconto())) {
            desconto = valorPedido.multiply(cupom.getValorDesconto())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            desconto = cupom.getValorDesconto().min(valorPedido);
        }

        cupom.setUsosAtuais((cupom.getUsosAtuais() != null ? cupom.getUsosAtuais() : 0) + 1);
        cupomRepository.save(cupom);

        return desconto;
    }

    private void validarCupom(Cupom cupom, BigDecimal valorPedido) {
        if (cupom.getAtivo() == null || !cupom.getAtivo()) {
            throw new IllegalArgumentException("Cupom inativo");
        }
        if (cupom.getDataValidade() != null && cupom.getDataValidade().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cupom expirado");
        }
        if (cupom.getUsoLimite() != null && cupom.getUsosAtuais() != null
            && cupom.getUsosAtuais() >= cupom.getUsoLimite()) {
            throw new IllegalArgumentException("Cupom esgotado");
        }
        if (cupom.getValorMinimoPedido() != null && valorPedido.compareTo(cupom.getValorMinimoPedido()) < 0) {
            throw new IllegalArgumentException(
                "Valor mínimo de R$ " + cupom.getValorMinimoPedido() + " não atingido");
        }
    }

    public Pedido atualizarStatus(UUID pedidoId, String status) {
        var pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado"));
        pedido.setStatus(status);
        if ("CONCLUIDO".equals(status)) {
            pedido.setDataConclusao(LocalDateTime.now());
        }
        return pedidoRepository.save(pedido);
    }

    public Pedido cancelar(UUID pedidoId) {
        var pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado"));
        if ("PAGO".equals(pedido.getStatus()) || "CONCLUIDO".equals(pedido.getStatus())) {
            throw new IllegalStateException("Pedido pago ou concluído não pode ser cancelado");
        }
        pedido.setStatus("CANCELADO");
        return pedidoRepository.save(pedido);
    }

    private String toCsv(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }
}
