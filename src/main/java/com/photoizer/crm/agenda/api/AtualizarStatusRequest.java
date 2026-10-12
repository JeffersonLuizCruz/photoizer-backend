package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AtualizarStatusRequest", description = "Novo status de um agendamento.")
public record AtualizarStatusRequest(
    @Schema(description = "Novo status", example = "CONFIRMADO",
        allowableValues = {"AGENDADO", "CONFIRMADO", "REALIZADO", "EM_EDICAO", "FINALIZADO",
            "CANCELADO", "NO_SHOW", "REAGENDADO"})
    @NotBlank String status
) {}
