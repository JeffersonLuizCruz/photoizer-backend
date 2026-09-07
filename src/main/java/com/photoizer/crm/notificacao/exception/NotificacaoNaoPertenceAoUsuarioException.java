package com.photoizer.crm.notificacao.exception;

import com.photoizer.crm.shared.exception.ErrorCode;

/**
 * Exceção lançada quando um usuário tenta acessar notificação de outro usuário.
 * HTTP 403 Forbidden.
 */
public class NotificacaoNaoPertenceAoUsuarioException extends NotificacaoBusinessException {

    public NotificacaoNaoPertenceAoUsuarioException() {
        super(ErrorCode.NOTIFICACAO_NAO_PERTENCE_AO_USUARIO, "Notificação não pertence ao usuário autenticado");
    }
}
