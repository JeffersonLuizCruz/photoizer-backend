package com.photoizer.crm.edicao.exception;

/**
 * Exceção base para todas as exceções de domínio do módulo edição.
 * Exception Hierarchy — base comum facilita tratamento centralizado
 * e segue padrão aprovado no DEBT.md.
 */
public class EdicaoBusinessException extends RuntimeException {

    public EdicaoBusinessException(String message) {
        super(message);
    }

    public EdicaoBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
