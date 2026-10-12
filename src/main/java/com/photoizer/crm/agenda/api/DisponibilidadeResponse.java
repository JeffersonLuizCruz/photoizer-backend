package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "DisponibilidadeResponse", description = "Resultado da verificação de disponibilidade de horário.")
public record DisponibilidadeResponse(
    @Schema(description = "Indica se o horário está livre") boolean disponivel,
    @Schema(description = "Conflitos encontrados") List<Conflito> conflitos) {

    @Schema(name = "ConflitoAgenda", description = "Conflito de horário com outro agendamento.")
    public record Conflito(
        @Schema(description = "ID do agendamento conflitante") UUID agendamentoId,
        @Schema(description = "Horário do conflito") String horario,
        @Schema(description = "Nome do cliente") String clienteNome) {}
}
