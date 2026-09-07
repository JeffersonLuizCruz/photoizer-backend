package com.photoizer.crm.agenda.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado quando um agendamento é criado.
 *
 * PATTERN: Event Enrichment (Modulith).
 * Os campos clienteNome e fotografoIds são resolvidos pelo publisher (que tem acesso
 * ao agendamento) e embutidos no evento para que listeners de outros módulos
 * (ex.: notificacao) não precisem acessar repositórios do agenda — eliminando
 * violação de fronteira entre módulos e risco de LazyInitializationException.
 */
public record AgendamentoCriadoEvent(
    UUID agendamentoId,
    UUID clienteId,
    UUID pacoteId,
    LocalDateTime dataHoraEnsaio,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    BigDecimal percentualComissao,
    BigDecimal valorBasePacote,
    String clienteNome,
    List<UUID> fotografoIds
) {
}
