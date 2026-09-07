package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para falhas de autenticação (HTTP 401).
 * Substitui: SessaoInvalidaException, BadCredentialsException (indiretamente).
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message);
    }
}
