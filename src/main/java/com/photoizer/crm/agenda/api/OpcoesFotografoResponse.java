package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "OpcoesFotografoResponse",
    description = "Fotógrafos ativos e o responsável pré-selecionado para novos agendamentos.")
public record OpcoesFotografoResponse(
    @Schema(description = "Fotógrafos ativos disponíveis para seleção")
    List<FotografoOpcao> fotografos,
    @Schema(description = "Fotógrafo pré-selecionado; null quando não há padrão definido", nullable = true)
    UUID fotografoPadraoId
) {
    @Schema(name = "FotografoOpcao", description = "Fotógrafo disponível para seleção.")
    public record FotografoOpcao(UUID id, String nome) {}
}
