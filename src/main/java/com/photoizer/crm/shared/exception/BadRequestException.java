package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para requisições inválidas (HTTP 400).
 * Substitui: TipoComprovanteInvalidoException, IllegalArgumentException
 * em contextos de API.
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
