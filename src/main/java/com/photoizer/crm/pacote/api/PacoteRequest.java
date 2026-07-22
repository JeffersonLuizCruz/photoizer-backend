package com.photoizer.crm.pacote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record PacoteRequest(
    @NotBlank String nome,
    String descricao,
    @Positive int quantidadeFotos,
    @PositiveOrZero int quantidadeVideos,
    @Positive BigDecimal valorBase,
    String duracaoEstimada,
    boolean bloqueiaDiaInteiro,
    boolean ativo,
    UUID fotografoId,
    UUID editorResponsavelId,
    @PositiveOrZero Integer diasParaEntrega
) {
}
