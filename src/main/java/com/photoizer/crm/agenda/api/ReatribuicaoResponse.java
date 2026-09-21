package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.ReatribuicaoAgendamento;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReatribuicaoResponse(
    UUID id,
    UUID fotografoAnteriorId,
    String fotografoAnteriorNome,
    UUID novoFotografoId,
    String novoFotografoNome,
    String solicitanteNome,
    String motivo,
    LocalDateTime createdAt
) {
    public static ReatribuicaoResponse of(ReatribuicaoAgendamento r) {
        return new ReatribuicaoResponse(
            r.getId(),
            r.getFotografoAnterior() != null ? r.getFotografoAnterior().getId() : null,
            r.getFotografoAnterior() != null ? r.getFotografoAnterior().getNome() : null,
            r.getNovoFotografo() != null ? r.getNovoFotografo().getId() : null,
            r.getNovoFotografo() != null ? r.getNovoFotografo().getNome() : null,
            r.getSolicitante() != null ? r.getSolicitante().getNome() : null,
            r.getMotivo(),
            r.getAuditInfo() != null ? r.getAuditInfo().getCreatedAt() : null
        );
    }
}
