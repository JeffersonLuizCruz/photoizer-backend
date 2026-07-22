package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.CompraExtra;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CompraExtraResponse(
    UUID id,
    UUID agendamentoId,
    BigDecimal valorTotal,
    String status,
    String urlComprovante,
    LocalDateTime dataPagamento
) {
    public static CompraExtraResponse of(CompraExtra c) {
        return new CompraExtraResponse(
            c.getId(), c.getAgendamentoId(), c.getValorTotal(),
            c.getStatus(), c.getUrlComprovante(), c.getDataPagamento()
        );
    }
}
