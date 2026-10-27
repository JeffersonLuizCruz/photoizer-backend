package com.photoizer.crm.foto.acl;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projeção leve de um agendamento para o painel de armazenamento.
 *
 * <p>O status é exposto como texto e o predicado {@code finalizado} já vem
 * resolvido: o módulo {@code foto} não precisa (e não deve) conhecer o enum
 * {@code StatusAgendamento} do módulo {@code agenda}.
 */
public record AgendamentoResumo(
    UUID id,
    String clienteNome,
    LocalDateTime dataHoraEnsaio,
    String status,
    boolean finalizado
) {}
