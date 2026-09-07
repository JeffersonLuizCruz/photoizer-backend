package com.photoizer.crm.notificacao.exception;

import java.util.UUID;

/**
 * Exceção lançada quando uma notificação não é encontrada pelo ID.
 * HTTP 404 Not Found.
 */
public class NotificacaoNaoEncontradaException extends NotificacaoBusinessException {

    public NotificacaoNaoEncontradaException(UUID id) {
        super("Notificação não encontrada: " + id);
    }
}
