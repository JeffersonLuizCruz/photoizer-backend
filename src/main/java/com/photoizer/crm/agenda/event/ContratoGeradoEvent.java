package com.photoizer.crm.agenda.event;

import java.util.UUID;

/**
 * Publicado quando o módulo 'documento' gera com sucesso o PDF de contrato para um agendamento.
 * O módulo 'agenda' consome este evento para marcar o flag contratoGerado = true,
 * mantendo a máquina de estados do agendamento sob seu domínio (evita escrita cross-module).
 */
public record ContratoGeradoEvent(
    UUID agendamentoId
) {
}
