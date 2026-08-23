package com.photoizer.crm.agenda.service;

import java.util.UUID;

public record ConflitoAgendaParams(
    UUID fotografoId,
    UUID excluirAgendamentoId
) {
    public static ConflitoAgendaParams paraCriacao(UUID fotografoId) {
        return new ConflitoAgendaParams(fotografoId, null);
    }

    public static ConflitoAgendaParams paraAtualizacao(UUID fotografoId, UUID excluirAgendamentoId) {
        return new ConflitoAgendaParams(fotografoId, excluirAgendamentoId);
    }

    public static ConflitoAgendaParams semFotografo() {
        return new ConflitoAgendaParams(null, null);
    }

    public static ConflitoAgendaParams semFotografo(UUID excluirAgendamentoId) {
        return new ConflitoAgendaParams(null, excluirAgendamentoId);
    }
}
