package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Exception Hierarchy
 *
 * Base para todas as exceções de negócio do sistema.
 * Cada exceção carrega um HttpStatus (para mapeamento HTTP) e opcionalmente
 * um ErrorCode (para identificação programática pelo cliente da API).
 *
 * Motivo: O GlobalExceptionHandler atual importa 60+ classes de exceção
 * de 14 módulos de negócio (violação Modulith — infraestrutura depende
 * do domínio). Com esta hierarquia, cada módulo usa subclasses marcadoras
 * (NotFoundException, ConflictException, etc.) e o handler reduz de
 * 50+ métodos para 5 métodos genéricos.
 *
 * Subclasses:
 * - NotFoundException (404)
 * - BadRequestException (400)
 * - ConflictException (409)
 * - UnprocessableException (422)
 * - GoneException (410)
 * - UnauthorizedException (401)
 * - ForbiddenException (403)
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final ErrorCode errorCode;

    public BusinessException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = null;
    }

    public BusinessException(HttpStatus httpStatus, ErrorCode errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public BusinessException(HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = null;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
