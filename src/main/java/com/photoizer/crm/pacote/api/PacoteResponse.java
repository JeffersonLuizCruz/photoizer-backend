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
    String duracaoEstimada,
    boolean bloqueiaDiaInteiro,
    boolean ativo,
    Integer diasParaEntrega,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
