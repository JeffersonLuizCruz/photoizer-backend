package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record EnviarSelecaoRequest(
    @NotEmpty(message = "Selecione ao menos uma foto do pacote")
    List<UUID> fotoIdsPacote,

    @Size(max = 2000, message = "A observação deve ter no máximo 2000 caracteres")
    String observacao
) {}
