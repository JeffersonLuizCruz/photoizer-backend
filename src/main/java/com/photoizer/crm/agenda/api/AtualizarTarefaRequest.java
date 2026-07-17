package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.TipoTarefa;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record AtualizarTarefaRequest(
    @NotNull TipoTarefa tipo,
    UUID responsavelId,
    @NotNull LocalDateTime dataLimite
) {}
