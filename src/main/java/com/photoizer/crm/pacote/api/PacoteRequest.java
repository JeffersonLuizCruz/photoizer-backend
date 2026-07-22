package com.photoizer.crm.pacote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record PacoteRequest(
    @NotBlank String nome,
    String descricao,
    @Positive int quantidadeFotos,
    @PositiveOrZero int quantidadeVideos,
    @Positive BigDecimal valorBase,
    String duracaoEstimada,
    boolean bloqueiaDiaInteiro,
    boolean ativo
) {
}
