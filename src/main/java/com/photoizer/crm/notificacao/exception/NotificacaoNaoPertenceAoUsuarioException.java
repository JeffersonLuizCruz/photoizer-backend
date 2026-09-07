package com.photoizer.crm.notificacao.exception;

public class NotificacaoNaoPertenceAoUsuarioException extends RuntimeException {

    public NotificacaoNaoPertenceAoUsuarioException() {
        super("Notificação não pertence ao usuário autenticado");
    }
}
