package com.photoizer.crm.financeiro.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain Event — Publicado quando fotos ou vídeos extras são adicionados a um agendamento.
 * O módulo agenda consome este evento para atualizar valorExtras e valorTotalFinal do Agendamento.
 *
 * Pattern: Domain Event — elimina escrita cross-module direta.
 */
public record ExtrasAdicionadosEvent(
    UUID agendamentoId,
    String tipo,
    int quantidade,
    BigDecimal valorUnitario,
    BigDecimal valorTotal
) {}
