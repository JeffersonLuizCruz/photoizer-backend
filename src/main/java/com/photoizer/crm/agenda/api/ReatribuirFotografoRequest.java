package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(name = "ReatribuirFotografoRequest", description = "Dados para transferência de fotógrafo de um ensaio.")
public record ReatribuirFotografoRequest(
    @Schema(description = "ID do novo fotógrafo") @NotNull UUID fotografoId,
    @Schema(description = "Motivo da transferência", nullable = true) String motivo
) {
}
