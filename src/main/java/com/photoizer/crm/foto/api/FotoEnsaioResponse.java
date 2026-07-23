package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.FotoEnsaio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    LocalDateTime createdAt,
    String titulo,
    String descricao,
    List<String> tags,
    String categoria,
    LocalDate dataSessao,
    String metadataExif,
    boolean destaque
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
            f.getStatus().name(),
            f.isSelecionadaPacote(),
            f.getCompraExtraId(),
            f.getCreatedAt(),
            f.getTitulo(),
            f.getDescricao(),
            f.getTags(),
            f.getCategoria(),
            f.getDataSessao(),
            f.getMetadataExif(),
            f.isDestaque()
        );
    }
}
