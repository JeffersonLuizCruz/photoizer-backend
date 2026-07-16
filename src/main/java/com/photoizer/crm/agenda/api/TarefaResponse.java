package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Tarefa;

import java.time.LocalDateTime;
import java.util.UUID;

public record TarefaResponse(
    UUID id,
    UUID agendamentoId,
    String tipo,
    UUID responsavelId,
    String responsavelNome,
    LocalDateTime dataLimite,
    LocalDateTime dataConclusao,
    String status
) {
    public static TarefaResponse of(Tarefa t) {
        return new TarefaResponse(
            t.getId(),
            t.getAgendamento().getId(),
            t.getTipo().name(),
            t.getResponsavel() != null ? t.getResponsavel().getId() : null,
            t.getResponsavel() != null ? t.getResponsavel().getNome() : null,
            t.getDataLimite(),
            t.getDataConclusao(),
            t.getStatus().name()
        );
    }
}
