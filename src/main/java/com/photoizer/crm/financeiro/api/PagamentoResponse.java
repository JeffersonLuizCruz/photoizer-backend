package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.shared.model.TipoPagamento;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.shared.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoResponse(
    UUID id,
    UUID agendamentoId,
    BigDecimal valor,
    LocalDateTime dataPagamento,
    String urlComprovante,
    String observacao,
    UUID compraExtraId,
    FormaPagamento formaPagamento,
    TipoPagamento tipoPagamento
) {
    public static PagamentoResponse of(Pagamento p) {
        return new PagamentoResponse(
            p.getId(),
            p.getAgendamento().getId(),
            p.getValor(),
            p.getDataPagamento(),
            p.getUrlComprovante(),
            p.getObservacao(),
            p.getCompraExtraId(),
            p.getFormaPagamento(),
            p.getTipoPagamento()
        );
    }
}