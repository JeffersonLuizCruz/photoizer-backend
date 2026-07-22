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
    UUID fotografoId,
    String fotografoNome,
    UUID editorResponsavelId,
    String editorResponsavelNome,
    Integer diasParaEntrega,
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
            p.getFotografo() != null ? p.getFotografo().getId() : null,
            p.getFotografo() != null ? p.getFotografo().getNome() : null,
            p.getEditorResponsavel() != null ? p.getEditorResponsavel().getId() : null,
            p.getEditorResponsavel() != null ? p.getEditorResponsavel().getNome() : null,
            p.getDiasParaEntrega(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
