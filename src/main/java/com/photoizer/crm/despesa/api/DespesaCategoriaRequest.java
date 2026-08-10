package com.photoizer.crm.despesa.api;

import jakarta.validation.constraints.NotBlank;

public record DespesaCategoriaRequest(
    @NotBlank String nome,
    String cor,
    Boolean ativo,
    Integer ordem
) {}
