package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RecusarPropostaRequest", description = "Motivo da recusa de uma proposta.")
public record RecusarPropostaRequest(
    @Schema(description = "Motivo da recusa", example = "Cliente desistiu do ensaio")
    @NotBlank(message = "O motivo da recusa é obrigatório")
    @Size(max = 500, message = "O motivo da recusa deve ter no máximo 500 caracteres")
    String motivo
) {
}
