package com.photoizer.crm.foto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "RemoverFotosLoteRequest", description = "IDs das fotos que serão removidas em lote")
public record RemoverFotosLoteRequest(
    @Schema(description = "IDs das fotos a remover", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    @NotEmpty(message = "Informe ao menos uma foto para remover")
    List<@NotNull(message = "O ID da foto não pode ser nulo") UUID> fotoIds
) {}
