package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AvaliacaoRequest(
    @NotNull UUID clienteId,
    UUID agendamentoId,
    UUID pacoteId,
    @NotNull @Min(1) @Max(5) int pontuacao,
    String comentario,
    boolean depoimento
) {}
