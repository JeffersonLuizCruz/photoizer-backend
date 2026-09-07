package com.photoizer.crm.agenda.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado quando o pagamento final de um agendamento é registrado.
 *
 * PATTERN: Event Enrichment (Modulith).
 * Os campos clienteNome e fotografoIds são resolvidos pelo publisher (que tem acesso
 * ao agendamento) e embutidos no evento para que listeners de outros módulos
 * (ex.: notificacao) não precisem acessar repositórios do agenda — eliminando
 * violação de fronteira entre módulos e risco de LazyInitializationException.
 */
public record PagamentoFinalRegistradoEvent(
    UUID agendamentoId,
    BigDecimal valorPago,
    String clienteNome,
    List<UUID> fotografoIds
) {
}
