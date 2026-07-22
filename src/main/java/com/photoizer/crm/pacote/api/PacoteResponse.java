package com.photoizer.crm.pacote.api;

import com.photoizer.crm.pacote.model.Pacote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PacoteResponse(
    UUID id,
    String nome,
    String descricao,
    int quantidadeFotos,
    int quantidadeVideos,
    BigDecimal valorBase,
    String duracaoEstimada,
    boolean bloqueiaDiaInteiro,
    boolean ativo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PacoteResponse of(Pacote p) {
        return new PacoteResponse(
            p.getId(),
            p.getNome(),
            p.getDescricao(),
            p.getQuantidadeFotos(),
            p.getQuantidadeVideos(),
            p.getValorBase(),
            p.getDuracaoEstimada(),
            p.getBloqueiaDiaInteiro(),
            p.getAtivo(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
