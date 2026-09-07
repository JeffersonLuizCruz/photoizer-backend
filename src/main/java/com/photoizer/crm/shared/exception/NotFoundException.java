package com.photoizer.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * PATTERN: Marker Subclass
 *
 * Exceção para recursos não encontrados (HTTP 404).
 * Substitui ~20 classes de exceção espalhadas nos módulos:
 * ClienteNaoEncontradoException, AgendamentoNaoEncontradoException,
 * EdicaoNaoEncontradaException, GaleriaNaoEncontradaException, etc.
 *
 * Cada módulo pode manter sua classe de domínio como subclass
 * (backward-compatible) ou usar diretamente:
 *   throw new NotFoundException("Cliente não encontrado: " + id);
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}
