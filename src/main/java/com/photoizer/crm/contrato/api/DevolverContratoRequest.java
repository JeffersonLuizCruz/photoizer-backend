package com.photoizer.crm.contrato.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DevolverContratoRequest(
    @Size(max = 30) String tipoMotivo,
    @NotBlank String motivo
) {
}