package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para erros de validação de negócio (HTTP 422).
 * Substitui ~25 classes: PacoteInativoException, AgendamentoNoPassadoException,
 * CarrinhoVazioException, LimitePacoteExcedidoException, etc.
 */
public class UnprocessableException extends BusinessException {

    public UnprocessableException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public UnprocessableException(ErrorCode errorCode, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }
}
