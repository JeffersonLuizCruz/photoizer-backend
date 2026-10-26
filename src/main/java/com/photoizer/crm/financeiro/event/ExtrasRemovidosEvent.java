package com.photoizer.crm.financeiro.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain Event — Publicado quando uma compra extra do e-commerce é cancelada.
 * O módulo agenda consome este evento para remover o valor de
 * valorExtras/valorTotalFinal e recalcular o saldo do agendamento.
 *
 * Pattern: Domain Event — mantém a escrita em Agendamento no módulo agenda.
 */
public record ExtrasRemovidosEvent(
    UUID agendamentoId,
    BigDecimal valorTotal
) {}
