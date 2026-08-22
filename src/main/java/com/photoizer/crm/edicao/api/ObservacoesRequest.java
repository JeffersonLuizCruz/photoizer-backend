package com.photoizer.crm.edicao.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Type-safe DTO para atualização de observações.
 * Substitui Map<String,String> que era usada no controller.
 */
public record ObservacoesRequest(
    @NotBlank String observacoes
) {}
