package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarioRequest(
    @NotBlank(message = "O comentário não pode ser vazio")
    @Size(max = 2000, message = "O comentário deve ter no máximo 2000 caracteres")
    String mensagem,

    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    String autorNome
) {}