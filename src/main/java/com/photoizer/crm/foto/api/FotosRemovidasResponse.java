package com.photoizer.crm.foto.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FotosRemovidasResponse", description = "Resultado da remoção de fotos em lote")
public record FotosRemovidasResponse(
    @Schema(description = "Quantidade de fotos removidas", example = "12")
    int removidas
) {}
