package com.photoizer.crm.agenda.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento publicado quando o fotógrafo responsável de um agendamento é transferido.
 *
 * PATTERN: Event Enrichment (Modulith) — clienteNome e dataHoraEnsaio são resolvidos
 * pelo publisher para que o listener de notificação não acesse repositórios do agenda.
 */
public record AgendamentoReatribuidoEvent(
    UUID agendamentoId,
    String clienteNome,
    LocalDateTime dataHoraEnsaio,
    UUID fotografoAnteriorId,
    UUID novoFotografoId
) {
}
