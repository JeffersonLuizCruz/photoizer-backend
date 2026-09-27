package com.photoizer.crm.auth.event;

import java.util.UUID;

/**
 * Evento publicado quando um parceiro semeado legado não pôde ser removido
 * (por estar vinculado a agendamentos/edições/contratos) e foi apenas desativado.
 *
 * PATTERN: Domain Events (Modulith) — permite que o módulo agenda cancele
 * repasses pendentes do parceiro sem que o auth acesse repositórios do agenda.
 */
public record ParceiroSementeDesativadoEvent(UUID userId) {
}
