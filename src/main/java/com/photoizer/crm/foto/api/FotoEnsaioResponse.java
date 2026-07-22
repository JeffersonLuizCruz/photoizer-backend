package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.FotoEnsaio;

import java.time.LocalDateTime;
import java.util.UUID;

public record FotoEnsaioResponse(
    UUID id,
    UUID agendamentoId,
    String fileName,
    String originalUrl,
    String watermarkedUrl,
    String thumbUrl,
    int ordem,
    String status,
    boolean selecionadaPacote,
    UUID compraExtraId,
    LocalDateTime createdAt
) {
    public static FotoEnsaioResponse of(FotoEnsaio f) {
        return new FotoEnsaioResponse(
            f.getId(),
            f.getAgendamentoId(),
            f.getFileName(),
            "/api/v1/agendamentos/" + f.getAgendamentoId() + "/fotos/" + f.getId() + "/original",
            "/api/v1/ecommerce/fotos/" + f.getId() + "/watermarked",
            "/api/v1/agendamentos/" + f.getAgendamentoId() + "/fotos/" + f.getId() + "/thumb",
            f.getOrdem(),
            f.getStatus(),
            f.isSelecionadaPacote(),
            f.getCompraExtraId(),
            f.getCreatedAt()
        );
    }
}
