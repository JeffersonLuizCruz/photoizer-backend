package com.photoizer.crm.bloqueio.service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dados de criação/edição de um bloqueio de agenda.
 * profissionalId nulo representa bloqueio global (toda a equipe).
 */
public record BloqueioAgendaCommand(
    String titulo,
    String descricao,
    LocalDateTime inicio,
    LocalDateTime fim,
    UUID profissionalId
) {}
