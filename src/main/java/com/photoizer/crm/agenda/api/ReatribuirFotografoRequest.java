package com.photoizer.crm.agenda.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReatribuirFotografoRequest(
    @NotNull UUID fotografoId,
    String motivo
) {
}
