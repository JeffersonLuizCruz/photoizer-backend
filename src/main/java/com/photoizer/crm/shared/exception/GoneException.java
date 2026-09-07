package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para recursos expirados/removidos permanentemente (HTTP 410).
 * Substitui: TokenExpiradoException, ContratoTokenExpiradoException.
 */
public class GoneException extends BusinessException {

    public GoneException(String message) {
        super(HttpStatus.GONE, message);
    }

    public GoneException(ErrorCode errorCode, String message) {
        super(HttpStatus.GONE, errorCode, message);
    }
}
