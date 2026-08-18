package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoResponse(
    UUID id,
    UUID agendamentoId,
    BigDecimal valor,
    LocalDateTime dataPagamento,
    String urlComprovante,
    String observacao
) {
    public static PagamentoResponse of(Pagamento p) {
        return new PagamentoResponse(
            p.getId(),
            p.getAgendamento().getId(),
            p.getValor(),
            p.getDataPagamento(),
            p.getUrlComprovante(),
            p.getObservacao()
        );
    }
}