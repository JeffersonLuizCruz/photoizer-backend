package com.photoizer.crm.bloqueio.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "BloqueioAgendaResponse", description = "Compromisso/indisponibilidade da agenda.")
public record BloqueioAgendaResponse(
    UUID id,
    String titulo,
    String descricao,
    LocalDateTime inicio,
    LocalDateTime fim,
    UUID profissionalId,
    UUID criadoPorId,
    LocalDateTime criadoEm,
    boolean ativo
) {}
