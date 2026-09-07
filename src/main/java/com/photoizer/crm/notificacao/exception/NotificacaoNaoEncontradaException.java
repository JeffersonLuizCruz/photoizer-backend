package com.photoizer.crm.notificacao.exception;

import java.util.UUID;

public class NotificacaoNaoEncontradaException extends RuntimeException {

    public NotificacaoNaoEncontradaException(UUID id) {
        super("Notificação não encontrada: " + id);
    }
}
