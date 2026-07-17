package com.photoizer.crm.agenda.api;

import java.util.List;
import java.util.UUID;

public record DisponibilidadeResponse(boolean disponivel, List<Conflito> conflitos) {
    public record Conflito(UUID agendamentoId, String horario, String clienteNome) {}
}
