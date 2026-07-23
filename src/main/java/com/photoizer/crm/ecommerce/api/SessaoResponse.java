package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Sessao;

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
    String status,
    LocalDateTime createdAt
) {
    public static SessaoResponse of(Sessao s) {
        return new SessaoResponse(
            s.getId(), s.getClienteId(), s.getNomeSessao(), s.getDataRealizacao(),
            s.getLocal(), s.getDescricao(), s.getStatus(), s.getCreatedAt()
        );
    }
}
