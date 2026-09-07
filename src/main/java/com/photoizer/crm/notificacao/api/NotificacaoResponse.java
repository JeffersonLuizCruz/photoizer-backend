package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.model.TipoNotificacao;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para notificações.
 *
 * Mapeamento feito via NotificacaoMapper (MapStruct) — não usar static of().
 * Segue padrão dos módulos despesa, edicao e ecommerce.
 */
public record NotificacaoResponse(
    UUID id,
    String titulo,
    String mensagem,
    String link,
    TipoNotificacao tipo,
    boolean lida,
    LocalDateTime createdAt
) {
}
