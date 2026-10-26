package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

/**
 * Publicado quando uma foto sai do carrinho. Usado para remover o extra do
 * snapshot da seleção ativa enquanto ele ainda não foi editado.
 */
public record ItemCarrinhoRemovidoEvent(
    UUID agendamentoId,
    UUID fotoId
) {}
