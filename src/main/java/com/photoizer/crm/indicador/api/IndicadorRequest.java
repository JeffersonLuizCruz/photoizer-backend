package com.photoizer.crm.indicador.api;

import jakarta.validation.constraints.NotBlank;

public record IndicadorRequest(
    @NotBlank String nome,
    @NotBlank String telefone,
    String observacoes
) {}
