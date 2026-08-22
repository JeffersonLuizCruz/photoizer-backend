package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Sessao;
import com.photoizer.crm.ecommerce.model.StatusSessao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessaoResponse(
    UUID id,
    UUID clienteId,
    String nomeSessao,
    LocalDate dataRealizacao,
    String local,
    String descricao,
    StatusSessao status,
    LocalDateTime createdAt
) {
    public static SessaoResponse of(Sessao s) {
        return new SessaoResponse(
            s.getId(), s.getClienteId(), s.getNomeSessao(), s.getDataRealizacao(),
            s.getLocal(), s.getDescricao(), s.getStatus(), s.getAuditInfo().getCreatedAt()
        );
    }
}
