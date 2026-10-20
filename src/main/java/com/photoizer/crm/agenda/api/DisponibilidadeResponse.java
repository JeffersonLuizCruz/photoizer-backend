package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "DisponibilidadeResponse", description = "Resultado da verificação de disponibilidade de horário.")
public record DisponibilidadeResponse(
    @Schema(description = "Indica se o horário está livre") boolean disponivel,
    @Schema(description = "Conflitos com agendamentos") List<Conflito> conflitos,
    @Schema(description = "Compromissos/bloqueios que impedem o horário") List<BloqueioConflito> bloqueios) {

    @Schema(name = "ConflitoAgenda", description = "Conflito de horário com outro agendamento.")
    public record Conflito(
        @Schema(description = "ID do agendamento conflitante") UUID agendamentoId,
        @Schema(description = "Horário do conflito") String horario,
        @Schema(description = "Nome do cliente") String clienteNome) {}

    @Schema(name = "BloqueioAgendaConflito", description = "Compromisso que bloqueia o horário.")
    public record BloqueioConflito(
        @Schema(description = "ID do bloqueio") UUID bloqueioId,
        @Schema(description = "Título do compromisso") String titulo,
        @Schema(description = "Início do bloqueio") LocalDateTime inicio,
        @Schema(description = "Fim do bloqueio") LocalDateTime fim) {}
}
