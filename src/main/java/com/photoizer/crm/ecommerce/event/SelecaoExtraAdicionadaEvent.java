package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

/**
 * Uma foto extra foi anexada ao snapshot da seleção ativa depois do envio.
 * O módulo foto usa o evento para marcar {@code emSelecaoEdicao}.
 */
public record SelecaoExtraAdicionadaEvent(
    UUID agendamentoId,
    UUID selecaoId,
    UUID fotoId
) {}
