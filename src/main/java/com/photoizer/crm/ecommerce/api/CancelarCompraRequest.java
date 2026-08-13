package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.Size;

public record CancelarCompraRequest(
    @Size(max = 2000, message = "O motivo deve ter no máximo 2000 caracteres")
    String motivo
) {}