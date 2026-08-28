package com.photoizer.crm.financeiro.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain Event — Publicado quando um pagamento é registrado no financeiro.
 * O módulo agenda consome este evento para atualizar status e valores do Agendamento,
 * mantendo a máquina de estados como responsabilidade exclusiva do agenda.
 *
 * Pattern: Domain Event — elimina escrita cross-module direta.
 */
public record PagamentoRegistradoEvent(
    UUID agendamentoId,
    UUID pagamentoId,
    BigDecimal valor,
    boolean isExtraEcommerce,
    UUID compraExtraId
) {}
