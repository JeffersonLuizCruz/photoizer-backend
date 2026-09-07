package com.photoizer.crm.edicao.exception;

import com.photoizer.crm.shared.exception.BusinessException;
import com.photoizer.crm.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Exceção base para todas as exceções de domínio do módulo edição.
 * Exception Hierarchy — base comum facilita tratamento centralizado
 * e segue padrão aprovado no DEBT.md.
 */
public class EdicaoBusinessException extends BusinessException {

    public EdicaoBusinessException(HttpStatus httpStatus, ErrorCode errorCode, String message) {
        super(httpStatus, errorCode, message);
    }

    public EdicaoBusinessException(HttpStatus httpStatus, String message) {
        super(httpStatus, message);
    }

    public EdicaoBusinessException(HttpStatus httpStatus, String message, Throwable cause) {
        super(httpStatus, message, cause);
    }
}
