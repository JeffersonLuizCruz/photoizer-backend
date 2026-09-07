package com.photoizer.crm.notificacao.exception;

/**
 * Exceção base para todas as exceções de domínio do módulo notificação.
 *
 * PATTERN: Exception Hierarchy
 *
 * Motivo: Base comum facilita tratamento centralizado no GlobalExceptionHandler
 * e segue padrão já adotado no módulo edicao (EdicaoBusinessException).
 * Permite adicionar código de domínio eHttpStatus no futuro (DEBT.md §2 shared).
 */
public class NotificacaoBusinessException extends RuntimeException {

    public NotificacaoBusinessException(String message) {
        super(message);
    }

    public NotificacaoBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
