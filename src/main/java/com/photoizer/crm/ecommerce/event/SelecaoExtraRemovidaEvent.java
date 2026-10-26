package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

/**
 * Uma foto extra foi removida do snapshot da seleção ativa antes de ser
 * editada. O módulo foto usa o evento para desmarcar {@code emSelecaoEdicao}.
 */
public record SelecaoExtraRemovidaEvent(
    UUID agendamentoId,
    UUID selecaoId,
    UUID fotoId
) {}
