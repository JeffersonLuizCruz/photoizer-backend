package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para acesso negado (HTTP 403).
 * Substitui: FotoNaoPertenceAoAgendamentoException,
 * NotificacaoNaoPertenceAoUsuarioException.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
