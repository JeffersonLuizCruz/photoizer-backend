package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.ExtraServico;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExtraServicoResponse(
    UUID id,
    UUID agendamentoId,
    String tipo,
    int quantidade,
    BigDecimal valorUnitario,
    BigDecimal valorTotal,
    LocalDateTime createdAt
) {
    public static ExtraServicoResponse of(ExtraServico e) {
        return new ExtraServicoResponse(
            e.getId(),
            e.getAgendamento().getId(),
            e.getTipo().name(),
            e.getQuantidade(),
            e.getValorUnitario(),
            e.getValorTotal(),
            e.getAuditInfo().getCreatedAt()
        );
    }
}
