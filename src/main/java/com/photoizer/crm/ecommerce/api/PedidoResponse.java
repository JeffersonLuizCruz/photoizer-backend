package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PedidoResponse(
    UUID id,
    UUID clienteId,
    UUID pacoteId,
    UUID agendamentoId,
    BigDecimal subtotalPacote,
    BigDecimal subtotalExtras,
    BigDecimal taxaEntrega,
    BigDecimal desconto,
    BigDecimal total,
    String status,
    String formaPagamento,
    String opcaoEntrega,
    LocalDateTime dataPedido,
    LocalDateTime dataConclusao
) {
    public static PedidoResponse of(Pedido p) {
        return new PedidoResponse(
            p.getId(), p.getClienteId(), p.getPacoteId(), p.getAgendamentoId(),
            p.getSubtotalPacote(), p.getSubtotalExtras(), p.getTaxaEntrega(),
            p.getDesconto(), p.getTotal(), p.getStatus(), p.getFormaPagamento(),
            p.getOpcaoEntrega(), p.getDataPedido(), p.getDataConclusao()
        );
    }
}
