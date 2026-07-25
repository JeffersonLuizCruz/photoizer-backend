package com.photoizer.crm.edicao.api;

import com.photoizer.crm.edicao.model.Edicao;

import java.time.LocalDateTime;
import java.util.UUID;

public record EdicaoResponse(
    UUID id,
    UUID agendamentoId,
    String status,
    UUID fotografoId,
    String fotografoNome,
    UUID editorId,
    String editorNome,
    LocalDateTime dataEnvioRaw,
    LocalDateTime dataEnvioEditado,
    String observacoes,
    int totalFotosRaw,
    int totalFotosEditadas,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static EdicaoResponse of(Edicao e, int totalRaw, int totalEditadas) {
        return new EdicaoResponse(
            e.getId(),
            e.getAgendamentoId(),
            e.getStatus().name(),
            e.getFotografo() != null ? e.getFotografo().getId() : null,
            e.getFotografo() != null ? e.getFotografo().getNome() : null,
            e.getEditor() != null ? e.getEditor().getId() : null,
            e.getEditor() != null ? e.getEditor().getNome() : null,
            e.getDataEnvioRaw(),
            e.getDataEnvioEditado(),
            e.getObservacoes(),
            totalRaw,
            totalEditadas,
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
