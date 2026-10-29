package com.photoizer.crm.agenda.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento publicado quando um agendamento entra em EM_EDICAO (entrega pendente)
 * ou quando o job diário identifica entregas que continuam pendentes.
 *
 * PATTERN: Event Enrichment (Modulith).
 * Os dados necessários para a notificação são resolvidos pelo publisher (que
 * tem acesso ao agendamento), evitando que o módulo notificacao acesse
 * repositórios do agenda.
 */
public record EntregaPendenteEvent(
    UUID agendamentoId,
    String clienteNome,
    String pacoteNome,
    LocalDateTime dataHoraEnsaio,
    boolean lembreteDiario
) {
}
