package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

/**
 * Publicado quando uma foto entra no carrinho. Usado para anexar o extra ao
 * snapshot da seleção ativa durante a edição.
 */
public record ItemCarrinhoAdicionadoEvent(
    UUID agendamentoId,
    UUID fotoId
) {}
