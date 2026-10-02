package com.photoizer.crm.agenda.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecusarPropostaRequest(
    @NotBlank(message = "O motivo da recusa é obrigatório")
    @Size(max = 500, message = "O motivo da recusa deve ter no máximo 500 caracteres")
    String motivo
) {
}
