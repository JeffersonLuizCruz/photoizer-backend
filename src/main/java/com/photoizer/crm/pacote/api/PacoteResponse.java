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
    BigDecimal precoFotoExtra,
    String imagemCapa,
    String beneficios,
    BigDecimal valorTotalMinimo,
    String duracaoEstimada,
    boolean bloqueiaDiaInteiro,
    boolean ativo,
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
            p.getPrecoFotoExtra(),
            p.getImagemCapa(),
            p.getBeneficios(),
            p.getValorBase(),
            p.getDuracaoEstimada(),
            p.getBloqueiaDiaInteiro(),
            p.getAtivo(),
            p.getDiasParaEntrega(),
            p.getAuditInfo().getCreatedAt(),
            p.getAuditInfo().getUpdatedAt()
        );
    }
}
