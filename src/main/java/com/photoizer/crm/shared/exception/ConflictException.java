package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para conflitos de estado/dados (HTTP 409).
 * Substitui ~10 classes: ConflitoDeAgendaException, FotoJaSelecionadaException,
 * CompraJaPagaException, IndicadorDuplicadoException, etc.
 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
