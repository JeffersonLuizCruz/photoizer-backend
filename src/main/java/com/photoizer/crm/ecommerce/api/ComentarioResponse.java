package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.FotoComentario;

import java.time.LocalDateTime;
import java.util.UUID;

public record ComentarioResponse(
    UUID id,
    UUID fotoId,
    String autorNome,
    String mensagem,
    String origem,
    boolean lida,
    LocalDateTime createdAt
) {
    public static ComentarioResponse of(FotoComentario c) {
        return new ComentarioResponse(
            c.getId(),
            c.getFotoId(),
            c.getAutorNome(),
            c.getMensagem(),
            c.getOrigem().name(),
            c.isLida(),
            c.getCreatedAt()
        );
    }
}