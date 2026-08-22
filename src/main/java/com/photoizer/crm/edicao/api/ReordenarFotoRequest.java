package com.photoizer.crm.edicao.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Type-safe DTO para reordenação de fotos.
 * Substitui List<Map<String,Object>> que era usada no controller/service,
 * eliminando parsing manual e risco de ClassCastException em runtime.
 */
public record ReordenarFotoRequest(
    @NotNull UUID id,
    @NotNull @Min(0) int ordem
) {}
