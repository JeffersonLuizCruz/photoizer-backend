package com.photoizer.crm.notificacao.exception;

import com.photoizer.crm.shared.exception.BusinessException;
import com.photoizer.crm.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Exceção base para todas as exceções de domínio do módulo notificação.
 *
 * PATTERN: Exception Hierarchy
 *
 * Motivo: Base comum facilita tratamento centralizado no GlobalExceptionHandler
 * e segue padrão já adotado no módulo edicao (EdicaoBusinessException).
 * Permite adicionar código de domínio eHttpStatus no futuro (DEBT.md §2 shared).
 */
public class NotificacaoBusinessException extends BusinessException {

    public NotificacaoBusinessException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public NotificacaoBusinessException(ErrorCode errorCode, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message);
    }

    public NotificacaoBusinessException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
