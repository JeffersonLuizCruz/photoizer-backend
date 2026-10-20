package com.photoizer.crm.bloqueio.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "BloqueioAgendaRequest", description = "Dados de criação/edição de um compromisso na agenda.")
public record BloqueioAgendaRequest(
    @Schema(description = "Título do compromisso", example = "Congresso de fotografia")
    @NotBlank @Size(max = 120) String titulo,

    @Schema(description = "Descrição opcional", nullable = true)
    @Size(max = 500) String descricao,

    @Schema(description = "Início do período (ISO-8601)", example = "2026-11-20T08:00:00")
    @NotNull LocalDateTime inicio,

    @Schema(description = "Fim do período, exclusivo (ISO-8601)", example = "2026-11-20T18:00:00")
    @NotNull LocalDateTime fim,

    @Schema(description = "Profissional bloqueado; nulo bloqueia toda a equipe", nullable = true)
    UUID profissionalId
) {}
