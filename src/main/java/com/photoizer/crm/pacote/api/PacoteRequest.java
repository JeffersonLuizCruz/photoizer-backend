package com.photoizer.crm.pacote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record PacoteRequest(
    @NotBlank String nome,
    String descricao,
    @Positive int quantidadeFotos,
    @PositiveOrZero int quantidadeVideos,
    @Positive BigDecimal valorBase,
    @PositiveOrZero BigDecimal precoFotoExtra,
    String imagemCapa,
    String beneficios,
    String duracaoEstimada,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato hexadecimal #RRGGBB")
    String cor,
    boolean bloqueiaDiaInteiro,
    boolean ativo,
    @PositiveOrZero Integer diasParaEntrega
) {
}
