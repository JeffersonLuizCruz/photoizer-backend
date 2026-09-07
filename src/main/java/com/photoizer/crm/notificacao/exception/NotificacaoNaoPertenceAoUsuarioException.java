package com.photoizer.crm.notificacao.exception;

/**
 * Exceção lançada quando um usuário tenta acessar notificação de outro usuário.
 * HTTP 403 Forbidden.
 */
public class NotificacaoNaoPertenceAoUsuarioException extends NotificacaoBusinessException {

    public NotificacaoNaoPertenceAoUsuarioException() {
        super("Notificação não pertence ao usuário autenticado");
    }
}
