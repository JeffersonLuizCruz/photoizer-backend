package com.photoizer.crm.indicador.exception;

import java.util.UUID;

/**
 * Exceção de domínio lançada quando um indicador não é encontrado.
 * Mapeada para HTTP 404 NOT_FOUND pelo GlobalExceptionHandler.
 */
public class IndicadorNaoEncontradoException extends RuntimeException {

    public IndicadorNaoEncontradoException(UUID id) {
        super("Indicador não encontrado: " + id);
    }

    public IndicadorNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
