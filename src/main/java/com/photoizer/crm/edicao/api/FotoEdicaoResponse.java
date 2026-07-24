package com.photoizer.crm.edicao.api;

import com.photoizer.crm.edicao.model.FotoEdicao;

import java.time.LocalDateTime;
import java.util.UUID;

public record FotoEdicaoResponse(
    UUID id,
    UUID edicaoId,
    String rawFileName,
    String rawDownloadUrl,
    String rawPreviewUrl,
    String editedFileName,
    String editedDownloadUrl,
    String editedPreviewUrl,
    String status,
    int ordem,
    LocalDateTime createdAt
) {
    public static FotoEdicaoResponse of(FotoEdicao f) {
        var fotoBase = "/api/v1/edicao/fotos/" + f.getId();
        return new FotoEdicaoResponse(
            f.getId(),
            f.getEdicaoId(),
            f.getRawFileName(),
            fotoBase + "/raw",
            fotoBase + "/raw-preview",
            f.getEditedFileName(),
            f.getEditedPath() != null ? fotoBase + "/edited" : null,
            f.getEditedPath() != null ? fotoBase + "/edited-preview" : null,
            f.getStatus().name(),
            f.getOrdem(),
            f.getCreatedAt()
        );
    }
}
