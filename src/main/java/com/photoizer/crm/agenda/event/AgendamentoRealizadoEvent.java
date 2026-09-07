package com.photoizer.crm.agenda.event;

import java.util.List;
import java.util.UUID;

/**
 * Evento publicado quando um agendamento é marcado como realizado.
 *
 * PATTERN: Event Enrichment (Modulith).
 * Os campos clienteNome e fotografoIds são resolvidos pelo publisher (que tem acesso
 * ao agendamento) e embutidos no evento para que listeners de outros módulos
 * (ex.: notificacao) não precisem acessar repositórios do agenda — eliminando
 * violação de fronteira entre módulos e risco de LazyInitializationException.
 */
public record AgendamentoRealizadoEvent(
    UUID agendamentoId,
    UUID clienteId,
    String clienteNome,
    List<UUID> fotografoIds
) {
}
