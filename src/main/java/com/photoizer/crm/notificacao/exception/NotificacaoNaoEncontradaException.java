package com.photoizer.crm.notificacao.exception;

import com.photoizer.crm.shared.exception.ErrorCode;

import java.util.UUID;

/**
 * Exceção lançada quando uma notificação não é encontrada pelo ID.
 * HTTP 404 Not Found.
 */
public class NotificacaoNaoEncontradaException extends NotificacaoBusinessException {

    public NotificacaoNaoEncontradaException(UUID id) {
        super(ErrorCode.NOTIFICACAO_NAO_ENCONTRADA, "Notificação não encontrada: " + id);
    }
}
